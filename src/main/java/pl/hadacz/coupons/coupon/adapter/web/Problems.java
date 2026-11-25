package pl.hadacz.coupons.coupon.adapter.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/// Builds RFC 9457 problem details with the extra `errorCode` member.
final class Problems {

    /// How long a client should wait before retrying a temporary failure.
    private static final String RETRY_AFTER_SECONDS = "5";

    private Problems() {
    }

    static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setProperty("errorCode", code.name());
        return problem;
    }

    static ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return problem;
    }

    static ResponseEntity<Object> response(HttpStatus status, ErrorCode code, String detail) {
        return response(problem(status, code, detail));
    }

    static ResponseEntity<Object> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /// 503 with `Retry-After`: the request was fine, a dependency was not; trying again later may work.
    static ResponseEntity<Object> unavailable(ErrorCode code, String detail) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, code, detail));
    }
}
