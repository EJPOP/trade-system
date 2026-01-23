package top.tradesystem.krx.exception;

public class KrxApiException extends RuntimeException {
    private final int status;

    public KrxApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
