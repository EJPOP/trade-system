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

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAny(Exception e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        pd.setTitle("Server Error");
        return pd;
    }
}
