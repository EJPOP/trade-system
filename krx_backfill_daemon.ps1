<#
krx_backfill_daemon.ps1
- Runs until you stop it (Ctrl+C).
- Backfills KRX daily PRICE + TRADE in reverse (recent -> past) by YEAR, one year per cycle.
- Markets: KOSPI + KOSDAQ (avoids market=ALL validation issues).
- Writes logs and a checkpoint so it can resume after restart.

UPDATED:
- Adds per-year retry limit (MaxRetriesPerYear). After N failed cycles, moves to previous year.
- Persists retryCount in krx_backfill_state.json
#>

# -----------------------------
# CONFIG (edit these)
# -----------------------------
$BaseUrl    = "http://localhost:7777"
$Markets    = @("KOSPI","KOSDAQ")     # do NOT use ALL if your API returns 400 for it
$StartDate  = "19560303"             # inclusive lower bound
$EndDate    = "20260123"             # inclusive upper bound (today snapshot)
$SleepMins  = 1                      # wait minutes between cycles
$TimeoutSec = 180                    # per request timeout
$MaxRetriesPerYear = 1               # number of failed cycles allowed before moving on (0 = never retry)

# Endpoints (adjust if your routes differ)
$PriceEndpoint = "/api/krx/prices/daily/sync-range"
$TradeEndpoint = "/api/krx/trades/daily/sync/range"

# Files (created next to this ps1)
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogFile    = Join-Path $ScriptDir "krx_backfill_daemon.log"
$ErrFile    = Join-Path $ScriptDir "krx_backfill_daemon.err.log"
$StateFile  = Join-Path $ScriptDir "krx_backfill_state.json"

# -----------------------------
# Helpers
# -----------------------------
function Write-Log([string]$msg) {
  $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
  Write-Host $line
  Add-Content -Path $LogFile -Value $line -Encoding utf8
}

function Write-Err([string]$msg) {
  $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
  Write-Host $line
  Add-Content -Path $ErrFile -Value $line -Encoding utf8
}

function To-Year([string]$yyyymmdd) { return [int]$yyyymmdd.Substring(0,4) }

function Year-Range([int]$year) {
  # Returns @{ from="YYYYMMDD"; to="YYYYMMDD" } within StartDate/EndDate bounds
  if ($year -eq (To-Year $StartDate)) { $from = $StartDate } else { $from = "{0}0101" -f $year }
  if ($year -eq (To-Year $EndDate))   { $to   = $EndDate   } else { $to   = "{0}1231" -f $year }
  return @{ from=$from; to=$to }
}

function Load-State {
  if (Test-Path $StateFile) {
    try { return (Get-Content -Raw -Encoding utf8 $StateFile | ConvertFrom-Json) } catch {}
  }
  # default state
  return [pscustomobject]@{
    nextYear   = (To-Year $EndDate)   # start from the most recent year
    retryCount = 0                    # failed cycles for current year
  }
}

function Save-State($state) {
  ($state | ConvertTo-Json -Depth 5) | Out-File -FilePath $StateFile -Encoding utf8
}

function Invoke-Post([string]$url) {
  try {
    # Invoke-RestMethod doesn't easily give status code; we treat success as "no throw"
    return Invoke-RestMethod -Method Post -Uri $url -TimeoutSec $TimeoutSec -ErrorAction Stop
  } catch {
    # Try to extract response body for debugging
    $body = ""
    try {
      $resp = $_.Exception.Response
      if ($resp -and $resp.GetResponseStream()) {
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $body = $reader.ReadToEnd()
      }
    } catch {}
    $status = ""
    try { $status = [int]$_.Exception.Response.StatusCode } catch {}
    throw [System.Exception]::new(("HTTP_ERROR status={0} url={1} body={2}" -f $status, $url, $body), $_.Exception)
  }
}

# -----------------------------
# Main daemon loop
# -----------------------------
$state = Load-State
Write-Log "START daemon. BaseUrl=$BaseUrl Markets=$($Markets -join ',') Range=$EndDate -> $StartDate SleepMins=$SleepMins MaxRetriesPerYear=$MaxRetriesPerYear"
Write-Log "StateFile=$StateFile (nextYear=$($state.nextYear) retryCount=$($state.retryCount)) LogFile=$LogFile ErrFile=$ErrFile"

$minYear = To-Year $StartDate
$maxYear = To-Year $EndDate

while ($true) {
  $y = [int]$state.nextYear

  if ($y -lt $minYear) {
    Write-Log "Reached minYear=$minYear. Resetting nextYear to $maxYear (will effectively idle if already backfilled)."
    $state.nextYear = $maxYear
    $state.retryCount = 0
    Save-State $state
    Start-Sleep -Seconds ($SleepMins * 60)
    continue
  }

  $r = Year-Range $y
  $from = $r.from
  $to   = $r.to

  Write-Log ("CYCLE year={0} from={1} to={2} retryCount={3}" -f $y, $from, $to, $state.retryCount)

  $allOk = $true

  foreach ($m in $Markets) {
    # PRICE
    $priceUrl = "{0}{1}?from={2}&to={3}&market={4}" -f $BaseUrl, $PriceEndpoint, $from, $to, $m
    try {
      $res = Invoke-Post $priceUrl
      # Try to print a useful saved count if your API returns it
      $saved = $null
      if ($res -and ($res.PSObject.Properties.Name -contains "totalSaved")) { $saved = $res.totalSaved }
      elseif ($res -and ($res.PSObject.Properties.Name -contains "saved")) { $saved = $res.saved }
      Write-Log ("PRICE ok year={0} market={1} saved={2}" -f $y, $m, $saved)
    } catch {
      $allOk = $false
      Write-Err ("PRICE FAIL year={0} market={1} err={2}" -f $y, $m, $_.Exception.Message)
    }

    # TRADE
    $tradeUrl = "{0}{1}?from={2}&to={3}&market={4}" -f $BaseUrl, $TradeEndpoint, $from, $to, $m
    try {
      $res = Invoke-Post $tradeUrl
      $saved = $null
      if ($res -and ($res.PSObject.Properties.Name -contains "totalSaved")) { $saved = $res.totalSaved }
      elseif ($res -and ($res.PSObject.Properties.Name -contains "saved")) { $saved = $res.saved }
      Write-Log ("TRADE ok year={0} market={1} saved={2}" -f $y, $m, $saved)
    } catch {
      $allOk = $false
      Write-Err ("TRADE FAIL year={0} market={1} err={2}" -f $y, $m, $_.Exception.Message)
    }
  }

  if ($allOk) {
    # Move to previous year only if both markets succeeded for both endpoints
    $state.nextYear = $y - 1
    $state.retryCount = 0
    Save-State $state
    Write-Log ("DONE year={0}. nextYear={1}" -f $y, $state.nextYear)
  } else {
    # Failed cycle: either retry, or move on after MaxRetriesPerYear
    $state.retryCount = [int]$state.retryCount + 1

    if ($state.retryCount -gt $MaxRetriesPerYear) {
      Write-Err ("GIVE UP year={0} after retries={1}. Moving to nextYear={2}" -f $y, $state.retryCount, ($y - 1))
      $state.nextYear = $y - 1
      $state.retryCount = 0
      Save-State $state
    } else {
      Save-State $state
      Write-Err ("RETRY scheduled for year={0} (retryCount={1}/{2})" -f $y, $state.retryCount, $MaxRetriesPerYear)
    }
  }

  Write-Log ("SLEEP {0} minutes" -f $SleepMins)
  Start-Sleep -Seconds ($SleepMins * 60)
}
