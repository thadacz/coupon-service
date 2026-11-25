package pl.hadacz.coupons.coupon.adapter.web;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import pl.hadacz.coupons.coupon.domain.DomainValidationException;

/// Exceptions only for invalid input and technical failures. Business outcomes such as
/// "limit reached" are values returned by the use cases and mapped in the controllers.
///
/// Every error the API returns is a problem detail with an `errorCode`, including the ones
/// Spring MVC raises itself (unknown path, wrong method) and failures nobody planned for.
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainValidationException.class)
    ResponseEntity<Object> handleDomainValidation(DomainValidationException e) {
        return Problems.response(Problems.validationProblem(
                List.of(Map.of("field", e.field(), "message", e.getMessage()))));
    }

    /// The database is down, or every pooled connection stayed busy for the pool's timeout
    /// (2 s, on purpose: fail fast under overload). Both pass: 503 tells clients and load
    /// balancers to come back later instead of reporting a bug.
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
            TransientDataAccessException.class})
    ResponseEntity<Object> handleDatabaseUnavailable(Exception e) {
        log.warn("Database unavailable: {}", e.getMessage());
        return Problems.unavailable(ErrorCode.SERVICE_UNAVAILABLE,
                "The service is temporarily unable to handle the request. Try again shortly.");
    }

    /// A bug or an unforeseen failure: the stack trace goes to the log, never to the client.
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception e) {
        log.error("Unexpected error while handling a request", e);
        return Problems.response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        return Problems.response(Problems.validationProblem(errors));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return Problems.response(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Request body is missing or is not valid JSON.");
    }

    /// Spring MVC's own errors (404 for an unknown path, 405, 415...) get their problem detail
    /// built by the superclass; this adds the `errorCode` clients branch on.
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception e, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("errorCode"))) {
            problem.setProperty("errorCode", errorCodeFor(status).name());
        }
        return response;
    }

    private static ErrorCode errorCodeFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return status.value() == HttpStatus.BAD_REQUEST.value()
                ? ErrorCode.MALFORMED_REQUEST
                : ErrorCode.UNSUPPORTED_REQUEST;
    }
}
