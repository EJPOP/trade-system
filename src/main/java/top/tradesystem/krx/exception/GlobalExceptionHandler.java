package top.tradesystem.krx.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KrxApiException.class)
    public ProblemDetail handleKrx(KrxApiException e) {
        HttpStatus status = (e.getStatus() >= 400 && e.getStatus() < 600)
                ? HttpStatus.valueOf(e.getStatus())
                : HttpStatus.BAD_GATEWAY;

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle("KRX API Error");
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        pd.setTitle("Bad Request");
        return pd;
    }

    @ExceptionHandler(java.time.format.DateTimeParseException.class)
    public ProblemDetail handleDateParse(java.time.format.DateTimeParseException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid date format: " + e.getParsedString());
        pd.setTitle("Bad Request");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAny(Exception e) {
        // 내부 예외 메시지 노출 방지 — 로그에만 기록
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error");
        pd.setTitle("Server Error");
        return pd;
    }
}
