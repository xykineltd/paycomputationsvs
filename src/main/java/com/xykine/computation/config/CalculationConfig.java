package com.xykine.computation.config;

import com.xykine.computation.session.SessionCalculationObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CalculationConfig {

    @Bean
    public SessionCalculationObject employerBornTaxDetails(){
        return new SessionCalculationObject();
    }
}
