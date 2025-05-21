package com.xykine.computation;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication(scanBasePackages = "com.xykine")
@ConfigurationPropertiesScan
@RequiredArgsConstructor
public class ComputationApplication {

	public static void main(String[] args) {
		SpringApplication.run(ComputationApplication.class, args);
	}
}
