package com.fintechdemo.platform.observability;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Platform-wide observability defaults: JSON logs, and traces and metrics sent to the local OTLP collector.
 * They have the lowest priority, so a service's application.yml, environment variables or
 * command-line arguments always win.
 */
public class ObservabilityDefaultsPostProcessor implements EnvironmentPostProcessor {

    static final Map<String, Object> DEFAULTS = Map.of(
            "logging.structured.format.console", "ecs",
            "management.tracing.sampling.probability", "1.0",
            "management.opentelemetry.tracing.export.otlp.endpoint", "http://localhost:4318/v1/traces",
            "management.otlp.metrics.export.url", "http://localhost:4318/v1/metrics");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        DefaultPropertiesPropertySource.addOrMerge(DEFAULTS, environment.getPropertySources());
    }
}
