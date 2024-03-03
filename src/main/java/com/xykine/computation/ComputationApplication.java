package com.xykine.computation;

import com.xykine.computation.entity.Deductions;
import com.xykine.computation.entity.PensionFund;
import com.xykine.computation.entity.T511K;
import com.xykine.computation.entity.Tax;
import com.xykine.computation.repo.DeductionRepo;
import com.xykine.computation.repo.PensionFundRepo;
import com.xykine.computation.repo.T511KRepo;
import com.xykine.computation.repo.TaxRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootApplication
public class ComputationApplication implements CommandLineRunner {

	@Autowired
	private T511KRepo t511KRepo;
	@Autowired
	private TaxRepo taxRepo;
	@Autowired
	private DeductionRepo deductionRepo;
	@Autowired
	private PensionFundRepo pensionFundRepo;
	public static void main(String[] args) {
		SpringApplication.run(ComputationApplication.class, args);
	}

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
				.constant("ZTRC")
				.description("TData Allowant C")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.ZERO)
				.build();

		T511K securityAllowance = T511K.builder()
				.constant("ZSEC")
				.description("Security allowance band C")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(45000.00))
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

		 fueAllowance = T511K.builder()
				.constant("ZFUA")
				.description("fuel allowance band A")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.ZERO)
				.build();

		 carAllowance = T511K.builder()
				.constant("ZCAA")
				.description("Car allowance Maintenance band A")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				 .amount(BigDecimal.ZERO)
				.build();

		 driverAllowance = T511K.builder()
				.constant("ZDRA")
				.description("Driver allowance A")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				 .amount(BigDecimal.ZERO)
				.build();

		 dataAllowance = T511K.builder()
				.constant("Z1021")
				.description("Data allowance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(3000.00))
				.build();

		 lunchAllowance = T511K.builder()
				.constant("Z1022")
				.description("Lunch allowance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(18416.67))
				.build();

		 transportAllowance = T511K.builder()
				.constant("ZTRA")
				.description("TData Allowant C")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(1000.00))
				.build();

		 securityAllowance = T511K.builder()
				.constant("ZSEA")
				.description("Security allowance band A")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				 .amount(BigDecimal.valueOf(18416.67))
				.build();

		 stewardAllowance = T511K.builder()
				.constant("ZSTA")
				.description("Steward allowance band A")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				 .amount(BigDecimal.valueOf(18416.67))
				.build();

		t511KRepo.save(fueAllowance);
		t511KRepo.save(carAllowance);
		t511KRepo.save(driverAllowance);
		t511KRepo.save(dataAllowance);
		t511KRepo.save(lunchAllowance);
		t511KRepo.save(transportAllowance);
		t511KRepo.save(securityAllowance);
		t511KRepo.save(stewardAllowance);

		Tax taxClassA = Tax.builder()
				.taxClass("A")
				.band("A")
				.percentage(BigDecimal.valueOf(18.5))
				.build();

		Tax taxClassC = Tax.builder()
				.taxClass("C")
				.band("C")
				.percentage(BigDecimal.valueOf(25.8))
				.build();

		taxRepo.save(taxClassA);
		taxRepo.save(taxClassC);

		Deductions deductions1 = Deductions.builder()
				.employeeId("1")
				.amount(BigDecimal.valueOf(59000))
				.description("Loan from staff cooperative society")
				.active(true)
				.approvedBy("moruff")
				.createdBy("kazeem")
				.build();

		Deductions deductions2 = Deductions.builder()
				.employeeId("1")
				.amount(BigDecimal.valueOf(12000))
				.description("Membership fee")
				.active(true)
				.approvedBy("moruff")
				.createdBy("kazeem")
				.build();

		Deductions deductions3 = Deductions.builder()
				.employeeId("2")
				.amount(BigDecimal.valueOf(140000))
				.description("Car loan")
				.active(true)
				.approvedBy("moruff")
				.createdBy("kazeem")
				.build();

		Deductions deductions4 = Deductions.builder()
				.employeeId("2")
				.amount(BigDecimal.valueOf(20000))
				.description("External transfer")
				.active(true)
				.approvedBy("moruff")
				.createdBy("kazeem")
				.build();


		deductionRepo.save(deductions1);
		deductionRepo.save(deductions2);
		deductionRepo.save(deductions3);

		PensionFund pensionFund1 = PensionFund.builder()
				.employeeId("1")
				.account(1234567)
				.PFACode("STANBIC-IBTC")
				.percentage(BigDecimal.valueOf(7.5))
				.build();

		PensionFund pensionFund2 = PensionFund.builder()
				.employeeId("2")
				.account(4563637)
				.PFACode("ZENITH-PENSIONS")
				.percentage(BigDecimal.valueOf(7.5))
				.build();

		pensionFundRepo.save(pensionFund1);
		pensionFundRepo.save(pensionFund2);
	}
}
