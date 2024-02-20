package com.xykine.computation;

import com.xykine.computation.entity.T511K;
import com.xykine.computation.repo.T511KRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootApplication
public class ComputationApplication implements CommandLineRunner {
	public static void main(String[] args) {
		SpringApplication.run(ComputationApplication.class, args);
	}

	@Autowired
	private T511KRepo t511KRepo;

	@Override
	public void run(String... args) throws Exception {
		T511K fueAllowance = T511K.builder()
				.constant("ZFUC")
				.description("fuel allowance band C")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(37000.00))
				.build();

		T511K carAllowance = T511K.builder()
				.constant("ZCAC")
				.description("Car allowance Maintenance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(118538.34))
				.build();

		T511K driverAllowance = T511K.builder()
				.constant("ZDRC")
				.description("Driver allowance C")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(92307.60))
				.build();

		T511K dataAllowance = T511K.builder()
				.constant("Z1021")
				.description("Data allowance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(3000.00))
				.build();

		T511K lunchAllowance = T511K.builder()
				.constant("Z1022")
				.description("Lunch allowance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(18416.67))
				.build();

		T511K transportAllowance = T511K.builder()
				.constant("Z1022")
				.description("Transport allowance band C")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(12.00))
				.build();

		T511K securityAllowance = T511K.builder()
				.constant("ZSEC")
				.description("Security allowance band C")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(50000.00))
				.build();

		T511K stewardAllowance = T511K.builder()
				.constant("ZSTC")
				.description("Steward allowance band C")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(41000.00))
				.build();

		t511KRepo.save(fueAllowance);
		t511KRepo.save(carAllowance);
		t511KRepo.save(driverAllowance);
		t511KRepo.save(dataAllowance);
		t511KRepo.save(lunchAllowance);
		t511KRepo.save(transportAllowance);
		t511KRepo.save(securityAllowance);
		t511KRepo.save(stewardAllowance);
	}
}
