package com.xykine.computation;

import com.xykine.computation.entity.*;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.model.TaxBearer;
import com.xykine.computation.repo.*;
import com.xykine.computation.session.SessionCalculationObject;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;



@EnableCaching
@SpringBootApplication
@ConfigurationPropertiesScan
public class ComputationApplication implements CommandLineRunner {
	@Autowired
	private TaxRepo taxRepo;
	@Autowired
	private PensionFundRepo pensionFundRepo;
	@Autowired
	private ComputationConstantsRepo computationConstantsRepo;

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
				.taxClass("TaxClassA")
				.description(" <= 300,000 NGN")
				.percentage(BigDecimal.valueOf(7.0))
				.build();

		Tax taxClassB = Tax.builder()
				.taxClass("TaxClassB")
				.description(" > 300,000 NGN and <= 600,000 NGN")
				.percentage(BigDecimal.valueOf(11.0))
				.build();

		Tax taxClassC = Tax.builder()
				.taxClass("TaxClassC")
				.description(" > 600,000 NGN and <= 1,100,000 NGN")
				.percentage(BigDecimal.valueOf(15.0))
				.build();

		Tax taxClassD = Tax.builder()
				.taxClass("TaxClassD")
				.description(" > 1,100,000 NGN and <= 1,600,000 NGN")
				.percentage(BigDecimal.valueOf(19.0))
				.build();

		Tax taxClassE = Tax.builder()
				.taxClass("TaxClassE")
				.description(" > 1,600,000 NGN and <= 3,200,000 NGN")
				.percentage(BigDecimal.valueOf(21.0))
				.build();

		Tax taxClassF = Tax.builder()
				.taxClass("TaxClassF")
				.description(" > 3,200,000 NGN")
				.percentage(BigDecimal.valueOf(24.0))
				.build();

		taxRepo.save(taxClassA);
		taxRepo.save(taxClassB);
		taxRepo.save(taxClassC);
		taxRepo.save(taxClassD);
		taxRepo.save(taxClassE);
		taxRepo.save(taxClassF);

		ComputationConstants pensionFundPercent = ComputationConstants.builder()
				.id("pensionFundPercent")
				.description(" The percentage of basic salary and other relevant allowances that goes into employee´s pension")
				.value(BigDecimal.valueOf(0.08))
				.build();
		ComputationConstants nationalHousingFund = ComputationConstants.builder()
				.id("nationalHousingFundPercent")
				.description("The percentage of basic salary for national housing fund")
				.value(BigDecimal.valueOf(0.025))
				.build();
		ComputationConstants craFraction = ComputationConstants.builder()
				.id("craFraction")
				.description("Used to calculate fixed consolidated tax relief")
				.value(BigDecimal.valueOf(0.01))
				.build();
		ComputationConstants variableCRAFraction = ComputationConstants.builder()
				.id("variableCRAFraction")
				.description("Used to calculate variable consolidated tax relief")
				.value(BigDecimal.valueOf(0.20))
				.build();
		ComputationConstants craCutOff = ComputationConstants.builder()
				.id("craCutOff")
				.description("CRA cut off")
				.value(BigDecimal.valueOf(200000))
				.build();
		computationConstantsRepo.save(pensionFundPercent);
		computationConstantsRepo.save(nationalHousingFund);
		computationConstantsRepo.save(craFraction);
		computationConstantsRepo.save(craCutOff);
		computationConstantsRepo.save(variableCRAFraction);
	}
}
