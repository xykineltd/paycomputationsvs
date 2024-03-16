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
	@Autowired
	private AllowanceAndOtherPaymentsRepo allowanceAndOtherPaymentsRepo;
	public static void main(String[] args) {
		SpringApplication.run(ComputationApplication.class, args);
	}

	@Bean
	public SessionCalculationObject employerBornTaxDetails(){
		return new SessionCalculationObject();
	}

	@Override
	public void run(String... args) throws Exception {

		AllowanceAndOtherPayments fueAllowanceBandC = AllowanceAndOtherPayments.builder()
				.id("ZFUC")
				.bandCode("C")
				.taxPercent(BigDecimal.valueOf(8.5))
				.taxBearer(TaxBearer.EMPLOYEE)
				.description("Fuel allowance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(37000.00))
				.active(true)
				.build();
		allowanceAndOtherPaymentsRepo.save(fueAllowanceBandC);

		AllowanceAndOtherPayments carMaintenanceAllowance = AllowanceAndOtherPayments.builder()
				.id("ZCMC")
				.bandCode("C")
				.taxPercent(BigDecimal.valueOf(4.5))
				.taxBearer(TaxBearer.EMPLOYEE)
				.description("Car maintenance allowance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(45000.00))
				.active(true)
				.build();
		allowanceAndOtherPaymentsRepo.save(carMaintenanceAllowance);

		AllowanceAndOtherPayments driverAllowanceBandC = AllowanceAndOtherPayments.builder()
				.id("ZDRC")
				.bandCode("C")
				.description("Driver allowance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.taxPercent(BigDecimal.valueOf(4.5))
				.amount(BigDecimal.ZERO)
				.taxPercent(BigDecimal.ZERO)
				.active(true)
				.build();
		allowanceAndOtherPaymentsRepo.save(driverAllowanceBandC);

		AllowanceAndOtherPayments dataAllowanceAll = AllowanceAndOtherPayments.builder()
				.id("Z1021")
				.bandCode("ALL")
				.description("Data allowance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(3000))
				.taxPercent(BigDecimal.ZERO)
				.active(true)
				.build();
		allowanceAndOtherPaymentsRepo.save(dataAllowanceAll);

		AllowanceAndOtherPayments lunchAllowanceAll = AllowanceAndOtherPayments.builder()
				.id("Z1022")
				.bandCode("ALL")
				.description("Lunch Allowance")
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(18416.67))
				.taxPercent(BigDecimal.ZERO)
				.active(true)
				.build();
		allowanceAndOtherPaymentsRepo.save(lunchAllowanceAll);

		AllowanceAndOtherPayments transportAllowanceA = AllowanceAndOtherPayments.builder()
				.id("ZTRA")
				.bandCode("A")
				.description("Transport allowance")
				.taxPercent(BigDecimal.valueOf(4.5))
				.taxBearer(TaxBearer.EMPLOYER)
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(10000))
				.active(true)
				.build();
		allowanceAndOtherPaymentsRepo.save(transportAllowanceA);

		AllowanceAndOtherPayments securityAllowanceC = AllowanceAndOtherPayments.builder()
				.id("ZSEC")
				.bandCode("C")
				.description("Security allowance")
				.taxPercent(BigDecimal.valueOf(10.5))
				.taxBearer(TaxBearer.EMPLOYER)
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(45000))
				.active(true)
				.build();
		allowanceAndOtherPaymentsRepo.save(securityAllowanceC);

		AllowanceAndOtherPayments stewardAllowance = AllowanceAndOtherPayments.builder()
				.id("ZSTC")
				.bandCode("C")
				.description("Steward allowance")
				.taxPercent(BigDecimal.valueOf(10.5))
				.taxBearer(TaxBearer.EMPLOYEE)
				.startDate(LocalDate.parse("2020-01-01"))
				.endDate(LocalDate.parse("9999-12-31"))
				.amount(BigDecimal.valueOf(41000.00))
				.active(true)
				.build();
		allowanceAndOtherPaymentsRepo.save(stewardAllowance);

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
				.employeeId(1L)
				.amount(BigDecimal.valueOf(59000))
				.description("Loan from staff cooperative society")
				.active(true)
				.approvedBy("moruff")
				.createdBy("kazeem")
				.build();

		Deductions deductions2 = Deductions.builder()
				.employeeId(1L)
				.amount(BigDecimal.valueOf(12000))
				.description("Membership fee")
				.active(true)
				.approvedBy("moruff")
				.createdBy("kazeem")
				.build();

		Deductions deductions3 = Deductions.builder()
				.employeeId(2L)
				.amount(BigDecimal.valueOf(140000))
				.description("Car loan")
				.active(true)
				.approvedBy("moruff")
				.createdBy("kazeem")
				.build();

		Deductions deductions4 = Deductions.builder()
				.employeeId(2L)
				.amount(BigDecimal.valueOf(20000))
				.description("External transfer")
				.active(true)
				.approvedBy("moruff")
				.createdBy("kazeem")
				.build();


		deductionRepo.save(deductions1);
		deductionRepo.save(deductions2);
		deductionRepo.save(deductions3);
		deductionRepo.save(deductions4);

//		PensionFund pensionFund1 = PensionFund.builder()
//				.employeeId(1L)
//				.account(1234567)
//				.PFACode("STANBIC-IBTC")
//				.percentage(BigDecimal.valueOf(7.5))
//				.build();

		List<PensionFund> pensionFunds = new ArrayList<>(10);
		for (long i = 1; i <= 10; i++) {
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
