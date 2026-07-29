package com.xykine.computation.config;

import org.springframework.context.annotation.Configuration;

/**
 * Calculation session state is job-scoped via {@link com.xykine.computation.session.PayrollSessionHolder}.
 * Do not register SessionCalculationObject as a Spring singleton.
 */
@Configuration
public class CalculationConfig {
}
