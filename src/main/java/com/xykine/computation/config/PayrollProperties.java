package com.xykine.computation.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "xykine")
@Data
public class PayrollProperties {
    /**
     * Payroll UI URI .
     */
    @NotNull
    private String spaUri;
}