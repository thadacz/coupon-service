package pl.hadacz.coupons.coupon.adapter.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/// Builds RFC 9457 problem details with the extra `errorCode` member.
final class Problems {

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
}
