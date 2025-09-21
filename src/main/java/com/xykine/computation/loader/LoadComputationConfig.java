package com.xykine.computation.loader;


import com.xykine.computation.entity.*;
import com.xykine.computation.repo.*;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
//@Profile({"QA"})
@AllArgsConstructor
public class LoadComputationConfig {

    private final TaxRepo taxRepo;
	private final PensionFundRepo pensionFundRepo;
	private final ComputationConstantsRepo computationConstantsRepo;
	private final DashboardCardRepo dashboardCardRepo;
    private final EmployeeMetadataRepo employeeMetaDataRepo;
    private final CompanyMetaDataRepo companyMetadataRepo;


    @EventListener(ApplicationReadyEvent.class)
    public void loadLegalEntityTestData() {
        String oldTaxRule = """
    [
      {"limit": 300000, "rate": 7},
      {"limit": 300000, "rate": 11},
      {"limit": 500000, "rate": 15},
      {"limit": 500000, "rate": 19},
      {"limit": 1600000, "rate": 21},
      {"limit": null, "rate": 24}
    ]
    """;
        String newTaxRule = """
    [
      { "limit": 800000,    "rate": 0 },
      { "limit": 3000000,   "rate": 15 },
      { "limit": 12000000,  "rate": 18 },
      { "limit": 25000000,  "rate": 21 },
      { "limit": 50000000,  "rate": 23 },
      { "limit": null,      "rate": 25 }
    ]
    """;
        Tax nigeriaOldTaxRule = Tax.builder()
                .country("NIGERIA")
                .taxRule(oldTaxRule)
                .active(true)
                .build();

        Tax nigeriaNewTaxRule = Tax.builder()
                .country("NIGERIA")
                .taxRule(newTaxRule)
                .active(false)
                .build();

        taxRepo.save(nigeriaOldTaxRule);
        taxRepo.save(nigeriaNewTaxRule);

        ComputationConstants pensionFundPercent = ComputationConstants.builder()
                .id("pensionFundPercent")
                .description(" The percentage of basic salary and other relevant allowances that goes into employee´s pension")
                .value(BigDecimal.valueOf(0.08))
                .build();
        ComputationConstants employerPensionContributionPercent = ComputationConstants.builder()
                .id("employerPensionContributionPercent")
                .description("Employer pension contribution percentage")
                .value(BigDecimal.valueOf(0.10))
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
        ComputationConstants withHoldingTax = ComputationConstants.builder()
                .id("withHoldingTax")
                .description("WithHolding tax")
                .value(BigDecimal.valueOf(0.05))
                .build();

        computationConstantsRepo.save(pensionFundPercent);
        computationConstantsRepo.save(nationalHousingFund);
        computationConstantsRepo.save(craFraction);
        computationConstantsRepo.save(craCutOff);
        computationConstantsRepo.save(variableCRAFraction);
        computationConstantsRepo.save(employerPensionContributionPercent);
        computationConstantsRepo.save(withHoldingTax);

        DashboardCard dashboardCard = DashboardCard.builder()
                .id(UUID.randomUUID().toString())
                .totalOffCyclePayroll(0L)
                .totalRegularPayroll(0L)
                .totalPayrollCost(BigDecimal.ZERO)
                .averageEmployeeCost(BigDecimal.ZERO)
                .lastUpdatedAt(LocalDateTime.now())
                .build();

        if (dashboardCardRepo.findAll().size() == 0)
            dashboardCardRepo.save(dashboardCard);

        EmployeeMetadata contractStaff = EmployeeMetadata.builder()
                .employeeId("8e3b6e4952e8468a84fd84556f8fdf2a")
                .companyId("682cf69492b07e60fa109911")
                .employeeType(EmployeeType.CONTRACT)
                .isNHFSubscribed(false)
                .build();

        EmployeeMetadata regularStaffWithNHF = EmployeeMetadata.builder()
                .employeeId("682cf69592b07e60fa10991b")
                .companyId("682cf69492b07e60fa109911")
                .employeeType(EmployeeType.FULL_TIME)
                .isNHFSubscribed(false)
                .voluntaryPensionContribution(BigDecimal.ZERO)
                .build();

        EmployeeMetadata regularStaffNoNHF = EmployeeMetadata.builder()
                .employeeId("682cf69592b07e60fa10992a")
                .companyId("682cf69592b07e60fa10991b")
                .employeeType(EmployeeType.FULL_TIME)
                .isNHFSubscribed(false)
                .build();

        employeeMetaDataRepo.save(contractStaff);
        employeeMetaDataRepo.save(regularStaffWithNHF);
        employeeMetaDataRepo.save(regularStaffNoNHF);

        String morufoye_international_payment_distribution = """
    [
      {"type": "BASIC_SALARY_ANNUAL", "percentage": 16.46, "name": "Basic Salary"},
      {"type": "ALLOWANCE_ANNUAL_HOUSING", "percentage": 8.23, "name": "Housing Allowance"},
      {"type": "ALLOWANCE_ANNUAL_TRANSPORT", "percentage": 8.23, "name": "Transport Allowance"},
      {"type": "ALLOWANCE_ANNUAL", "percentage": 10, "name": "UTILITY"},
      {"type": "ALLOWANCE_ANNUAL", "percentage": 10, "name": "ENTERTAINMENT"},
      {"type": "ALLOWANCE_ANNUAL", "percentage": 17.08, "name": "PERSONAL OUTFIT"},
      {"type": "ALLOWANCE_ANNUAL", "percentage": 10, "name": "LEAVE"},
      {"type": "ALLOWANCE_ANNUAL", "percentage": 10, "name": "MEDICAL"},
      {"type": "ALLOWANCE_ANNUAL", "percentage": 10, "name": "TRAINING"}
    ]
    """;

        CompanyMetadata xykineCompanyMetadata = CompanyMetadata.builder()
                .companyId("682cf69492b07e60fa109911")
                .paymentEntryMode(PaymentFrequencyEnum.YEARLY)
                .salaryFrequency(PaymentFrequencyEnum.MONTHLY)
                .companyName("xykine inc")
                .build();
        companyMetadataRepo.save(xykineCompanyMetadata);

        CompanyMetadata morufoyeCompanyMetadata = CompanyMetadata.builder()
                .companyId("1234567")
                .paymentEntryMode(PaymentFrequencyEnum.YEARLY)
                .salaryFrequency(PaymentFrequencyEnum.MONTHLY)
                .companyName("morufoye international")
                .paymentDistribution(morufoye_international_payment_distribution)
                .build();
        companyMetadataRepo.save(morufoyeCompanyMetadata);
    }
}
