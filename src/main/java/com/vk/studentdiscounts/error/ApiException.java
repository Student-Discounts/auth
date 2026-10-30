package com.vk.studentdiscounts.error;

import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Ожидаемая ошибка бизнес-логики. Spring MVC превращает её в ответ application/problem+json (RFC 9457).
 */
public class ApiException extends ErrorResponseException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.status(), problemDetail(errorCode), null);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    private static ProblemDetail problemDetail(ErrorCode errorCode) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), errorCode.detail());
        problem.setProperty("code", errorCode.code());
        return problem;
    }
}
