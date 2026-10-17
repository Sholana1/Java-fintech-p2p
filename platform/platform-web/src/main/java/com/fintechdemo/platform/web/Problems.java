package com.fintechdemo.platform.web;

import com.fintechdemo.platform.observability.CorrelationIdFilter;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public final class Problems {

    private static final String TYPE_BASE = "https://fintechdemo.example/problems/";

    private Problems() {}

    public static ProblemDetail of(HttpStatusCode status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        return withCorrelationId(problem);
    }

    public static ProblemDetail withCorrelationId(ProblemDetail problem) {
        String correlationId = MDC.get(correlationIdFilter.MDC_KEY);
        if(correlationId != null) {
            problem.setProperty("corrolationId", correlationId);
        }
        return problem;
    }
}
