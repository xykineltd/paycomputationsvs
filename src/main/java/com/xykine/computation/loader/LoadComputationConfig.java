package com.xykine.computation.loader;


import com.xykine.computation.domain.LoanStatus;
import com.xykine.computation.entity.*;
import com.xykine.computation.repo.*;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

//@Component
//@Profile({"dev"})
@AllArgsConstructor
public class LoadComputationConfig {

    private final TaxRepo taxRepo;
	private final PensionFundRepo pensionFundRepo;
	private final ComputationConstantsRepo computationConstantsRepo;
	private final DashboardCardRepo dashboardCardRepo;
    private final EmployeeMetadataRepo employeeMetaDataRepo;
    private final CompanyMetaDataRepo companyMetadataRepo;
    private final LoanRepo loanRepo;
    private final PaymentSettingMetadataRepo paymentSettingMetadataRepo;

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
                .version("old")
                .active(false)
                .build();

        Tax nigeriaNewTaxRule = Tax.builder()
                .country("NIGERIA")
                .taxRule(newTaxRule)
                .version("new")
                .active(true)
                .build();

        taxRepo.deleteAll();

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

        //Delete everything so that we dont keep adding duplicate data every time we restart
        computationConstantsRepo.deleteAll();

        //Recreate
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
                .customTaxReliefApplicable(BigDecimal.ZERO)
                .voluntaryPensionContribution(BigDecimal.ZERO)
                .isPensioned(false)
                .build();

        EmployeeMetadata regularStaffWithNHF = EmployeeMetadata.builder()
                .employeeId("682cf69592b07e60fa10991b")
                .companyId("682cf69492b07e60fa109911")
                .isNHFSubscribed(false)

                .employeeType(EmployeeType.FULL_TIME)
                .isNHFSubscribed(false)
                .customTaxReliefApplicable(BigDecimal.ZERO)
                .voluntaryPensionContribution(BigDecimal.ZERO)
                .isPensioned(true)
                .build();

        EmployeeMetadata regularStaffNoNHF = EmployeeMetadata.builder()
                .employeeId("682cf69592b07e60fa10992a")
                .companyId("682cf69592b07e60fa10991b")
                .employeeType(EmployeeType.FULL_TIME)
                .isNHFSubscribed(false)
                .customTaxReliefApplicable(BigDecimal.ZERO)
                .voluntaryPensionContribution(BigDecimal.ZERO)
                .isPensioned(true)
                .build();

        employeeMetaDataRepo.deleteAll();

        EmployeeMetadata regularStaffWithCustomTaxReleif = EmployeeMetadata.builder()
                .employeeId("8654321")
                .companyId("1234567")
                .employeeType(EmployeeType.FULL_TIME)
                .isNHFSubscribed(false)
                .customTaxReliefApplicable(BigDecimal.valueOf(50000))
                .voluntaryPensionContribution(BigDecimal.ZERO)
                .isPensioned(true)
                .build();

        EmployeeMetadata regularStaffWithCustomTaxReleifAndVoluntaryPensionContribution = EmployeeMetadata.builder()
                .employeeId("standardWithVoluntaryPensionContribution")
                .companyId("1234567")
                .employeeType(EmployeeType.FULL_TIME)
                .isNHFSubscribed(false)
                .customTaxReliefApplicable(BigDecimal.ZERO)
                .voluntaryPensionContribution(BigDecimal.valueOf(1000))
                .isPensioned(true)
                .build();

        EmployeeMetadata standardNotPensioned = EmployeeMetadata.builder()
                .employeeId("standardNotPensioned")
                .companyId("1234567")
                .employeeType(EmployeeType.FULL_TIME)
                .isNHFSubscribed(false)
                .customTaxReliefApplicable(BigDecimal.ZERO)
                .voluntaryPensionContribution(BigDecimal.ZERO)
                .isPensioned(false)
                .build();

        EmployeeMetadata intern = EmployeeMetadata.builder()
                .employeeId("InternStaff")
                .companyId("1234567")
                .employeeType(EmployeeType.INTERN)
                .isNHFSubscribed(false)
                .customTaxReliefApplicable(BigDecimal.ZERO)
                .voluntaryPensionContribution(BigDecimal.ZERO)
                .isPensioned(false)
                .build();

        EmployeeMetadata gbagi = EmployeeMetadata.builder()
                .employeeId("7654321")
                .companyId("1234567")
                .employeeType(EmployeeType.FULL_TIME)
                .isNHFSubscribed(false)
                .customTaxReliefApplicable(BigDecimal.ZERO)
                .voluntaryPensionContribution(BigDecimal.valueOf(0))
                .isPensioned(true)
                .build();

        employeeMetaDataRepo.save(gbagi);
        employeeMetaDataRepo.save(intern);
        employeeMetaDataRepo.save(standardNotPensioned);
        employeeMetaDataRepo.save(regularStaffWithCustomTaxReleif);
        employeeMetaDataRepo.save(contractStaff);
        employeeMetaDataRepo.save(regularStaffWithNHF);
        employeeMetaDataRepo.save(regularStaffNoNHF);
        employeeMetaDataRepo.save(regularStaffWithCustomTaxReleifAndVoluntaryPensionContribution);

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


        CompanyMetadata xykineCompanyMetadata2 = CompanyMetadata.builder()
                .companyId("68dd326d1baabe7296f9624a")
                .paymentEntryMode(PaymentFrequencyEnum.YEARLY)
                .salaryFrequency(PaymentFrequencyEnum.MONTHLY)
                .paymentDistribution(morufoye_international_payment_distribution)
                .companyName("xykine")
                .build();

        CompanyMetadata morufoyeCompanyMetadata = CompanyMetadata.builder()
                .companyId("1234567")
                .paymentEntryMode(PaymentFrequencyEnum.YEARLY)
                .salaryFrequency(PaymentFrequencyEnum.MONTHLY)
                .companyName("morufoye international")
                .paymentDistribution(morufoye_international_payment_distribution)
                .build();

        CompanyMetadata moniepointMfbCompanyMetadata = CompanyMetadata.builder()
                .companyId("68e6121925592b68310c91cc")
                .paymentEntryMode(PaymentFrequencyEnum.YEARLY)
                .salaryFrequency(PaymentFrequencyEnum.MONTHLY)
                .paymentDistribution(morufoye_international_payment_distribution)
                .companyName("MonieWorld")
                .build();

        companyMetadataRepo.deleteAll();
        companyMetadataRepo.save(xykineCompanyMetadata);
        companyMetadataRepo.save(xykineCompanyMetadata2);
        companyMetadataRepo.save(morufoyeCompanyMetadata);
        companyMetadataRepo.save(moniepointMfbCompanyMetadata);

        Loan loan = Loan.builder()
                .companyId("1234567")
                .employeeId("7654321")
                .status(LoanStatus.APPROVED)
                .principalAmount(BigDecimal.valueOf(1000000))
                .outstandingAmount(BigDecimal.valueOf(1000000))
                .scheduledRepaymentAmount(BigDecimal.valueOf(10000))
                .description("Company Car Loan")
                .startDate(LocalDate.parse("2024-01-01"))
                .endDate(LocalDate.parse("2024-07-31"))
                .active(true)
                .build();

        Loan staffLoan = Loan.builder()
                .companyId("682cf69492b07e60fa109911")
                .employeeId("8e3b6e4952e8468a84fd84556f8fdf2a")
                .status(LoanStatus.APPROVED)
                .scheduledRepaymentAmount(BigDecimal.valueOf(10000))
                .description("Staff Loan")
                .active(true)
                .startDate(LocalDate.parse("2024-01-01"))
                .endDate(LocalDate.parse("2099-07-31"))
                .endDate(LocalDate.parse("2099-07-31"))
                .build();

        loanRepo.save(staffLoan);
        loanRepo.save(loan);

        PaymentSettingMetaData callAllowance = PaymentSettingMetaData.builder()
                .companyId("1234567")
                .employeeId("7654321")
                .paymentType("ALLOWANCE")
                .paymentName("Call Allowance")
                .startDate(LocalDate.parse("2025-01-01"))
                .endDate(LocalDate.parse("2025-07-31"))
                .prorated(false)
                .taxable(true)
                .build();


        PaymentSettingMetaData overtime = PaymentSettingMetaData.builder()
                .companyId("1234567")
                .employeeId("7654321")
                .paymentType("ALLOWANCE")
                .paymentName("OVERTIME GROSS")
                .startDate(LocalDate.parse("2025-01-01"))
                .endDate(LocalDate.parse("2025-07-31"))
                .prorated(false)
                .taxable(true)
                .build();

        PaymentSettingMetaData abasydoOffcycle = PaymentSettingMetaData.builder()
                .companyId("1234567")
                .employeeId("7654321")
                .paymentType("ALLOWANCE")
                .paymentName("13th month")
                .startDate(LocalDate.parse("2025-12-23"))
                .endDate(LocalDate.parse("2025-12-24"))
                .prorated(false)
                .taxable(false)
                .build();

        PaymentSettingMetaData abasydoOffcycle14 = PaymentSettingMetaData.builder()
                .companyId("1234567")
                .employeeId("7654321")
                .paymentType("ALLOWANCE")
                .paymentName("14th month")
                .startDate(LocalDate.parse("2025-12-23"))
                .endDate(LocalDate.parse("2025-12-24"))
                .prorated(false)
                .taxable(false)
                .build();

        paymentSettingMetadataRepo.save(abasydoOffcycle);
        paymentSettingMetadataRepo.save(abasydoOffcycle14);
        paymentSettingMetadataRepo.save(overtime);
        paymentSettingMetadataRepo.save(callAllowance);
    }
}

