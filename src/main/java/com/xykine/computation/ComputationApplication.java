package com.xykine.computation;

import com.xykine.computation.entity.*;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.model.TaxBearer;
import com.xykine.computation.repo.*;
import com.xykine.computation.session.SessionCalculationObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class ComputationApplication implements CommandLineRunner {
	@Autowired
	private TaxRepo taxRepo;
	@Autowired
	private DeductionRepo deductionRepo;
	@Autowired
	private PensionFundRepo pensionFundRepo;

	public static void main(String[] args) {
		SpringApplication.run(ComputationApplication.class, args);
	}

	@Bean
	public SessionCalculationObject employerBornTaxDetails(){
		return new SessionCalculationObject();
	}

	@Override
	public void run(String... args) throws Exception {

		Tax taxClassA = Tax.builder()
				.taxClass("A")
				.description(" <= 300,000 NGN")
				.percentage(BigDecimal.valueOf(7.0))
				.build();

		Tax taxClassB = Tax.builder()
				.taxClass("B")
				.description(" > 300,000 NGN and <= 600,000 NGN")
				.percentage(BigDecimal.valueOf(11.0))
				.build();

		Tax taxClassC = Tax.builder()
				.taxClass("C")
				.description(" > 600,000 NGN and <= 1,100,000 NGN")
				.percentage(BigDecimal.valueOf(15.0))
				.build();

		Tax taxClassD = Tax.builder()
				.taxClass("D")
				.description(" > 1,100,000 NGN and <= 1,600,000 NGN")
				.percentage(BigDecimal.valueOf(19.0))
				.build();

		Tax taxClassE = Tax.builder()
				.taxClass("E")
				.description(" > 1,600,000 NGN and <= 3,200,000 NGN")
				.percentage(BigDecimal.valueOf(21.0))
				.build();

		Tax taxClassF = Tax.builder()
				.taxClass("F")
				.description(" > 3,200,000 NGN")
				.percentage(BigDecimal.valueOf(24.0))
				.build();

		taxRepo.save(taxClassA);
		taxRepo.save(taxClassB);
		taxRepo.save(taxClassC);
		taxRepo.save(taxClassD);
		taxRepo.save(taxClassE);
		taxRepo.save(taxClassF);

		List<PensionFund> pensionFunds = new ArrayList<>(10);
		for (long i = 1; i <= 5; i++) {
			PensionFund pensionFund = PensionFund.builder()
					.employeeId(i)
					.account(i * 25)
					.PFACode("ZENITH-PENSIONS")
					.percentage(BigDecimal.valueOf(7.5))
					.build();

			pensionFunds.add(pensionFund);
		}

		pensionFundRepo.saveAll(pensionFunds);
	}
}
