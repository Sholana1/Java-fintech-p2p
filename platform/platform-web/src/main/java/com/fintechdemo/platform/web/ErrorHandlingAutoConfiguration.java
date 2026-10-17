package com.fintechdemo.platform.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

/** Gives every servlet service the same problem+json error responses, just by depending on platform-web. */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ErrorHandlingAutoConfiguration {

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
