package com.xykine.computation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xykine.computation.config.TestSecurityConfig;
import com.xykine.computation.entity.Loan;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.repo.LoanRepo;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import com.xykine.computation.repo.YTDReportRepo;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.DashboardCardResponse;
import com.xykine.computation.response.ReportResponse;

import com.xykine.computation.testdata.TestDataFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import org.xykine.payroll.model.PaymentInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;


import static com.xykine.computation.testdata.TestDataFactory.TEST_COMPANY_ID;
import static com.xykine.computation.testdata.TestDataFactory.TEST_EMPLOYEE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.xykine.payroll.model.MapKeys.NET_PAY;

@Slf4j
@SpringBootTest(classes = {ComputationApplication.class, TestSecurityConfig.class},webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
public class ControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private YTDReportRepo ytdReportRepo;
    @Autowired
    private PayrollReportDetailRepo payrollReportDetailRepo;
    @Autowired
    private PayrollReportSummaryRepo payrollReportSummaryRepo;
    @Autowired
    private LoanRepo loanRepo;

    protected static final Logger LOGGER = LoggerFactory.getLogger(ControllerIntegrationTest.class);

    @BeforeEach
    void setupReportTestData() {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("standard"));
        getReportSummary();
    }

    @AfterEach
    void cancelReport() {
        // cancel so the data can be reused for other ITs
        payrollReportDetailRepo.deleteAll();
        payrollReportSummaryRepo.deleteAll();
    }

    /****        COMPUTE CONTROLLER ENDPOINTS      *********/
    @Test
    void testStandard()  {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("standard"));
        ReportResponse reportSummary = getReportSummary();
        assertThat(reportSummary.getCode()).startsWith("PRR-");
        assert reportSummary != null;
        Map<String, Object> body = getReportDetail(reportSummary);
        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(1);
        });
        List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });
        PaymentInfo paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo).isNotNull().satisfies((x) -> {
                 assertThat(x.getNetPay()).isEqualByComparingTo(BigDecimal.valueOf(633334.0));
        });
        Map<String, BigDecimal> grossPay = paymentInfo.getGrossPay();
        assertThat(grossPay).isNotNull().satisfies((x) -> {
            assertThat(x.get("Transport Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("Housing Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("Training")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("PERSONAL OUTFIT")).isEqualByComparingTo(BigDecimal.valueOf(133308.61));
            assertThat(x.get("Utility")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Entertainment")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Leave")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Medical")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Basic Salary")).isEqualByComparingTo(BigDecimal.valueOf(128469.54));
            assertThat(x.get("Gross Pay")).isEqualByComparingTo(BigDecimal.valueOf(780495.34));
        });
        Map<String, BigDecimal> taxRelief = paymentInfo.getTaxRelief();
        assertThat(taxRelief).isNotNull().satisfies((x) -> {
            assertThat(x.get("CHARGEABLE INCOME")).isEqualByComparingTo(BigDecimal.valueOf(7863366.34));
            assertThat(x.get("ANNUAL VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("ANNUAL EMPLOYEE PENSION @ 8%")).isEqualByComparingTo(BigDecimal.valueOf(272625.9));
            assertThat(x.get("ANNUAL CONSOLIDATED ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2215841.58));
            assertThat(x.get("GROSS PAY (TAX PURPOSE)")).isEqualByComparingTo(BigDecimal.valueOf(10079207.92));
            assertThat(x.get("RELIEF ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2488467.48));
        });
        Map<String, BigDecimal> deduction = paymentInfo.getDeduction();
        assertThat(deduction).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Pension Fund")).isEqualByComparingTo(BigDecimal.valueOf(20555.13));
            assertThat(x.get("National Housing Fund")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Total Deduction")).isEqualByComparingTo(BigDecimal.valueOf(147162.07));
        });
        Map<String, BigDecimal> payeeTax = paymentInfo.getPayeeTax();
        assertThat(payeeTax).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("Taxable Income")).isEqualByComparingTo(BigDecimal.valueOf(7863366.34));
            assertThat(x.get("ANNUAL PAYEE TAX")).isEqualByComparingTo(BigDecimal.valueOf(1679207.92));
        });

        Map<String, BigDecimal> pension = paymentInfo.getPension();
        assertThat(pension).isNotNull().satisfies((x) -> {
            assertThat(x.get("Employer Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(25693.9));
            assertThat(x.get("Employee Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(20555.13));
            assertThat(x.get("VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Total Employee Pension")).isEqualByComparingTo(BigDecimal.valueOf(46249.03));
        });
    }

    @Test
    void testStandardWithDistributionList() throws InterruptedException {
        String companyId = "1234567";
        String startDate = "2025-06-01";
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("standard with payment distribution list"));
        ReportResponse reportSummary = getReportSummaryCustom(companyId);

        assert reportSummary != null;
        Map<String, Object> body = getReportDetail(reportSummary);
        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(1);
        });

        List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });

        PaymentInfo paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo).isNotNull().satisfies((x) -> {
            assertThat(x.getNetPay()).isEqualByComparingTo(BigDecimal.valueOf(623334.0));
        });

        Map<String, BigDecimal> grossPay = paymentInfo.getGrossPay();
        assertThat(grossPay).isNotNull().satisfies((x) -> {
            assertThat(x.get("Transport Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("Housing Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("TRAINING")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("PERSONAL OUTFIT")).isEqualByComparingTo(BigDecimal.valueOf(133308.61));
            assertThat(x.get("UTILITY")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("ENTERTAINMENT")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("LEAVE")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("MEDICAL")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Basic Salary")).isEqualByComparingTo(BigDecimal.valueOf(128469.54));
            assertThat(x.get("Gross Pay")).isEqualByComparingTo(BigDecimal.valueOf(780495.34));
        });
        Map<String, BigDecimal> taxRelief = paymentInfo.getTaxRelief();
        assertThat(taxRelief).isNotNull().satisfies((x) -> {
            assertThat(x.get("CHARGEABLE INCOME")).isEqualByComparingTo(BigDecimal.valueOf(7863366.34));
            assertThat(x.get("ANNUAL VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("ANNUAL EMPLOYEE PENSION @ 8%")).isEqualByComparingTo(BigDecimal.valueOf(272625.9));
            assertThat(x.get("ANNUAL CONSOLIDATED ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2215841.58));
            assertThat(x.get("GROSS PAY (TAX PURPOSE)")).isEqualByComparingTo(BigDecimal.valueOf(10079207.92));
            assertThat(x.get("RELIEF ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2488467.48));
        });
        Map<String, BigDecimal> deduction = paymentInfo.getDeduction();
        assertThat(deduction).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Pension Fund")).isEqualByComparingTo(BigDecimal.valueOf(20555.13));
            assertThat(x.get("National Housing Fund")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Company Car Loan")).isEqualByComparingTo(BigDecimal.valueOf(10000));  // Personal Deduction
            assertThat(x.get("Total Deduction")).isEqualByComparingTo(BigDecimal.valueOf(157162.07));
        });
        Map<String, BigDecimal> payeeTax = paymentInfo.getPayeeTax();
        assertThat(payeeTax).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("Taxable Income")).isEqualByComparingTo(BigDecimal.valueOf(7863366.34));
            assertThat(x.get("ANNUAL PAYEE TAX")).isEqualByComparingTo(BigDecimal.valueOf(1679207.92));
        });

        Map<String, BigDecimal> pension = paymentInfo.getPension();
        assertThat(pension).isNotNull().satisfies((x) -> {
            assertThat(x.get("Employer Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(25693.9));
            assertThat(x.get("Employee Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(20555.13));
            assertThat(x.get("VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Total Employee Pension")).isEqualByComparingTo(BigDecimal.valueOf(46249.03));
        });
        String employeeId = "7654321";
        String loanDescription = "Company Car Loan";
        Optional<Loan> loanOptional = loanRepo.findOneByCompanyIdAndEmployeeIdAndDescriptionAndActiveIsTrue(companyId, employeeId, loanDescription);

        assertThat(loanOptional.get()).isNotNull();
        assertThat(loanOptional.get().getOutstandingAmount()).isEqualTo(BigDecimal.valueOf(1000000));

        UpdateReportRequest updateReportRequest = new UpdateReportRequest();
        updateReportRequest.setPayrollStatus(PayrollStatus.APPROVED);
        updateReportRequest.setCompanyId(companyId);
        updateReportRequest.setStartDate(startDate);

        approvePayroll(updateReportRequest);

        /* Loan updates was executed asynchronously, so chill a lil bit before checking for update*/
        Thread.sleep(1000);

        loanOptional = loanRepo.findOneByCompanyIdAndEmployeeIdAndDescriptionAndActiveIsTrue(companyId, employeeId, loanDescription);
        assertThat(loanOptional.get().getOutstandingAmount()).isEqualTo(BigDecimal.valueOf(1000000).subtract(BigDecimal.valueOf(10000)));
    }

    @Test
    void testStandardWithDistributionListAndCustomTaxReleif() throws InterruptedException {
        String companyId = "1234567";
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("standard and performance with payment distribution list and custom tax refief"));
        ReportResponse reportSummary = getReportSummaryCustom(companyId);

        assert reportSummary != null;
        Map<String, Object> body = getReportDetail(reportSummary);
        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(1);
        });

        List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });

        PaymentInfo paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo).isNotNull().satisfies((x) -> {
            assertThat(x.getNetPay()).isEqualByComparingTo(BigDecimal.valueOf(798683.0));
        });

        Map<String, BigDecimal> grossPay = paymentInfo.getGrossPay();

        assertThat(grossPay).isNotNull().satisfies((x) -> {
            assertThat(x.get("Transport Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("Housing Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("TRAINING")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("PERSONAL OUTFIT")).isEqualByComparingTo(BigDecimal.valueOf(133308.61));
            assertThat(x.get("UTILITY")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("ENTERTAINMENT")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("LEAVE")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("MEDICAL")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Basic Salary")).isEqualByComparingTo(BigDecimal.valueOf(128469.54));
            assertThat(x.get("Gross Pay")).isEqualByComparingTo(BigDecimal.valueOf(956386.89));
        });
        Map<String, BigDecimal> taxRelief = paymentInfo.getTaxRelief();
        assertThat(taxRelief).isNotNull().satisfies((x) -> {
            assertThat(x.get("CHARGEABLE INCOME")).isEqualByComparingTo(BigDecimal.valueOf(7263366.34));
            assertThat(x.get("ANNUAL VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("CUSTOM TAX RELIEF APPLICABLE")).isEqualByComparingTo(BigDecimal.valueOf(50000));
            assertThat(x.get("ANNUAL EMPLOYEE PENSION @ 8%")).isEqualByComparingTo(BigDecimal.valueOf(272625.9));
            assertThat(x.get("ANNUAL CONSOLIDATED ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2215841.58));
            assertThat(x.get("GROSS PAY (TAX PURPOSE)")).isEqualByComparingTo(BigDecimal.valueOf(10079207.92));
            assertThat(x.get("GROSS PAY (TAX PURPOSE)")).isEqualByComparingTo(BigDecimal.valueOf(10079207.92));
            assertThat(x.get("RELIEF ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(3088467.48));
        });
        Map<String, BigDecimal> deduction = paymentInfo.getDeduction();
        assertThat(deduction).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(115749.8));
            assertThat(x.get("VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Pension Fund")).isEqualByComparingTo(BigDecimal.valueOf(20555.13));
            assertThat(x.get("National Housing Fund")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Total Deduction")).isEqualByComparingTo(BigDecimal.valueOf(157704.98));
        });
        Map<String, BigDecimal> payeeTax = paymentInfo.getPayeeTax();
        assertThat(payeeTax).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(115749.8));
            assertThat(x.get("Taxable Income")).isEqualByComparingTo(BigDecimal.valueOf(7263366.34));
            assertThat(x.get("ANNUAL PAYEE TAX")).isEqualByComparingTo(BigDecimal.valueOf(1535207.92));
        });

        Map<String, BigDecimal> pension = paymentInfo.getPension();
        assertThat(pension).isNotNull().satisfies((x) -> {
            assertThat(x.get("Employer Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(25693.9));
            assertThat(x.get("Employee Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(20555.13));
            assertThat(x.get("VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Total Employee Pension")).isEqualByComparingTo(BigDecimal.valueOf(46249.03));
        });
    }

    @Test
    void testContractStaffCompute()  {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("contract staff"));
        ReportResponse reportSummary = getReportSummary();
        Map<String, Object> body = getReportDetail(reportSummary);

        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(1);
        });

        List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });

        PaymentInfo paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo).isNotNull().satisfies((x) -> {
            assertThat(x.getNetPay()).isEqualByComparingTo(BigDecimal.valueOf(142500.0));
        });

        Map<String, BigDecimal> grossPay = paymentInfo.getGrossPay();
        assertThat(grossPay).isNotNull().satisfies((x) -> {
            assertThat(x.get("Gross Pay")).isEqualByComparingTo(BigDecimal.valueOf(150000.00));
            assertThat(x.get("Basic Salary")).isEqualByComparingTo(new BigDecimal(150000.00));
        });
        Map<String, BigDecimal> deduction = paymentInfo.getDeduction();
        assertThat(deduction).isNotNull().satisfies((x) -> {
            assertThat(x.get("WHT")).isEqualByComparingTo(BigDecimal.valueOf(7500.0));
            assertThat(x.get("Total Deduction")).isEqualByComparingTo(new BigDecimal(7500.0));
        });
    }

    // Test regular with performance bonus
    @Test
    void testStandardWithPerformanceBonusCompute() {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("standard with performance bonus"));
        ReportResponse reportSummary = getReportSummary();
        Map<String, Object> body = getReportDetail(reportSummary);
        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(1);
        });
        List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });

        PaymentInfo paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo).isNotNull().satisfies((x) -> {
            assertThat(x.getNetPay()).isEqualByComparingTo(BigDecimal.valueOf(787826.0));
        });

        Map<String, BigDecimal> grossPay = paymentInfo.getGrossPay();
        assertThat(grossPay).isNotNull().satisfies((x) -> {
            assertThat(x.get("Transport Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("OVERTIME GROSS")).isEqualByComparingTo(BigDecimal.valueOf(58817.24));
            assertThat(x.get("PERSONAL OUTFIT")).isEqualByComparingTo(BigDecimal.valueOf(133308.61));
            assertThat(x.get("Entertainment")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("MONTHLY PERFORMANCE BONUS")).isEqualByComparingTo(BigDecimal.valueOf(117074.31));
            assertThat(x.get("Leave")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Housing Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("Training")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Utility")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Basic Salary")).isEqualByComparingTo(BigDecimal.valueOf(128469.54));
            assertThat(x.get("Medical")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Gross Pay")).isEqualByComparingTo(BigDecimal.valueOf(956386.89));
        });

        Map<String, BigDecimal> taxRelief = paymentInfo.getTaxRelief();
        assertThat(taxRelief).isNotNull().satisfies((x) -> {
            assertThat(x.get("CHARGEABLE INCOME")).isEqualByComparingTo(BigDecimal.valueOf(7863366.34));
            assertThat(x.get("Fixed Consolidated Relief Allowance")).isEqualByComparingTo(BigDecimal.valueOf(33333.34));
            assertThat(x.get("ANNUAL EMPLOYEE PENSION @ 8%")).isEqualByComparingTo(BigDecimal.valueOf(272625.9));
            assertThat(x.get("Total Tax Relief")).isEqualByComparingTo(BigDecimal.valueOf(68511.66));
            assertThat(x.get("ANNUAL VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("ANNUAL CONSOLIDATED ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2215841.58));
            assertThat(x.get("Variable Consolidated Relief Allowance")).isEqualByComparingTo(BigDecimal.valueOf(35178.32));
            assertThat(x.get("GROSS PAY (TAX PURPOSE)")).isEqualByComparingTo(BigDecimal.valueOf(10079207.92));
            assertThat(x.get("RELIEF ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2488467.48));
        });

        Map<String, BigDecimal> deduction = paymentInfo.getDeduction();
        assertThat(deduction).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Pension Fund")).isEqualByComparingTo(BigDecimal.valueOf(20555.13));
            assertThat(x.get("Payee Tax on OVERTIME GROSS")).isEqualByComparingTo(BigDecimal.valueOf(5822.59));
            assertThat(x.get("Payee Tax on MONTHLY PERFORMANCE BONUS")).isEqualByComparingTo(BigDecimal.valueOf(15577.46));
            assertThat(x.get("National Housing Fund")).isEqualByComparingTo(BigDecimal.valueOf(0.0));
            assertThat(x.get("Total Deduction")).isEqualByComparingTo(BigDecimal.valueOf(168562.12));
        });

        Map<String, BigDecimal> payeeTax = paymentInfo.getPayeeTax();
        assertThat(payeeTax).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("Taxable Income")).isEqualByComparingTo(BigDecimal.valueOf(7863366.34));
            assertThat(x.get("Payee Tax on OVERTIME GROSS")).isEqualByComparingTo(BigDecimal.valueOf(5822.59));
            assertThat(x.get("Payee Tax on MONTHLY PERFORMANCE BONUS")).isEqualByComparingTo(BigDecimal.valueOf(15577.46));
            assertThat(x.get("ANNUAL PAYEE TAX")).isEqualByComparingTo(BigDecimal.valueOf(1679207.92));
        });
    }

    @Test
    void testStandardWithPerformanceBonusComputeWithPaymentDistributionList() {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("standard and performance with payment distribution list"));
        ReportResponse reportSummary = getReportSummary();
        Map<String, Object> body = getReportDetail(reportSummary);
        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(1);
        });
        List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });

        PaymentInfo paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo).isNotNull().satisfies((x) -> {
            assertThat(x.getNetPay()).isEqualByComparingTo(BigDecimal.valueOf(777826.0));
        });

        Map<String, BigDecimal> grossPay = paymentInfo.getGrossPay();
        assertThat(grossPay).isNotNull().satisfies((x) -> {
            assertThat(x.get("Transport Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("OVERTIME GROSS")).isEqualByComparingTo(BigDecimal.valueOf(58817.24));
            assertThat(x.get("PERSONAL OUTFIT")).isEqualByComparingTo(BigDecimal.valueOf(133308.61));
            assertThat(x.get("ENTERTAINMENT")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("MONTHLY PERFORMANCE BONUS")).isEqualByComparingTo(BigDecimal.valueOf(117074.31));
            assertThat(x.get("LEAVE")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Housing Allowance")).isEqualByComparingTo(BigDecimal.valueOf(64234.77));
            assertThat(x.get("TRAINING")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("UTILITY")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Basic Salary")).isEqualByComparingTo(BigDecimal.valueOf(128469.54));
            assertThat(x.get("MEDICAL")).isEqualByComparingTo(BigDecimal.valueOf(78049.53));
            assertThat(x.get("Gross Pay")).isEqualByComparingTo(BigDecimal.valueOf(956386.89));
        });

        Map<String, BigDecimal> taxRelief = paymentInfo.getTaxRelief();
        assertThat(taxRelief).isNotNull().satisfies((x) -> {
            assertThat(x.get("CHARGEABLE INCOME")).isEqualByComparingTo(BigDecimal.valueOf(7863366.34));
            assertThat(x.get("Fixed Consolidated Relief Allowance")).isEqualByComparingTo(BigDecimal.valueOf(33333.34));
            assertThat(x.get("ANNUAL EMPLOYEE PENSION @ 8%")).isEqualByComparingTo(BigDecimal.valueOf(272625.9));
            assertThat(x.get("Total Tax Relief")).isEqualByComparingTo(BigDecimal.valueOf(68511.66));
            assertThat(x.get("ANNUAL VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("ANNUAL CONSOLIDATED ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2215841.58));
            assertThat(x.get("Variable Consolidated Relief Allowance")).isEqualByComparingTo(BigDecimal.valueOf(35178.32));
            assertThat(x.get("GROSS PAY (TAX PURPOSE)")).isEqualByComparingTo(BigDecimal.valueOf(10079207.92));
            assertThat(x.get("RELIEF ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2488467.48));
        });

        Map<String, BigDecimal> deduction = paymentInfo.getDeduction();
        assertThat(deduction).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("VOLUNTARY PENSION CONTRIBUTION")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Pension Fund")).isEqualByComparingTo(BigDecimal.valueOf(20555.13));
            assertThat(x.get("Payee Tax on OVERTIME GROSS")).isEqualByComparingTo(BigDecimal.valueOf(5822.59));
            assertThat(x.get("Payee Tax on MONTHLY PERFORMANCE BONUS")).isEqualByComparingTo(BigDecimal.valueOf(15577.46));
            assertThat(x.get("National Housing Fund")).isEqualByComparingTo(BigDecimal.valueOf(0.0));
            assertThat(x.get("Company Car Loan")).isEqualByComparingTo(BigDecimal.valueOf(10000));  // Personal Deduction
            assertThat(x.get("Total Deduction")).isEqualByComparingTo(BigDecimal.valueOf(178562.12));
        });

        Map<String, BigDecimal> payeeTax = paymentInfo.getPayeeTax();
        assertThat(payeeTax).isNotNull().satisfies((x) -> {
            assertThat(x.get("MONTHLY PAYEE")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("Taxable Income")).isEqualByComparingTo(BigDecimal.valueOf(7863366.34));
            assertThat(x.get("Payee Tax on OVERTIME GROSS")).isEqualByComparingTo(BigDecimal.valueOf(5822.59));
            assertThat(x.get("Payee Tax on MONTHLY PERFORMANCE BONUS")).isEqualByComparingTo(BigDecimal.valueOf(15577.46));
            assertThat(x.get("ANNUAL PAYEE TAX")).isEqualByComparingTo(BigDecimal.valueOf(1679207.92));
        });
    }

    /****         REPORT CONTROLLER ENDPOINTS      *********/
    @Test
    void testGetReportByCompanyIdAndStatusAndVerifyApproveStatusIsFalse() {
        assertThat(getReportByCompanyIdAndStatus()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            assertThat(reportResponses.get(0).getPayrollStatus().compareTo(PayrollStatus.APPROVED) != 0);
        });
    }

    @Test
    void testGetReportByCompanyIdAndEmployeeId() {
        assertThat(getReportByCompanyIdAndEmployeeId()).isNotNull().satisfies(body -> {
            List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<>() {});
            assertThat(reportResponses.get(0).getEmployeeId()).isEqualTo(TEST_EMPLOYEE_ID);
        });
    }

    @Test
    void testGetReportByCompanyIdAndStartDate() {
        assertThat(getReportByStartDateAndCompanyId()).isNotNull().satisfies(body -> {
            assertThat(body.getStartDate()).isEqualTo(LocalDate.now().toString());
        });
    }

    @Test
    void testReportDetailsByCompanyIdAndEmployeeId() {
        assertThat(getPaymentDetailsByEmployeeAndCompanyId()).isNotNull().satisfies(body -> {
            assertThat(body.getEmployeeId()).isEqualTo(TEST_EMPLOYEE_ID);
            assertThat(body.getCompanyId()).isEqualTo(TestDataFactory.TEST_COMPANY_ID);
        });
    }

    @Test
    void testGetYTDReport() {
        //
        ytdReportRepo.deleteAll();
        assertThat(getYtdReport()).isNotNull().satisfies(body -> {
            YTDReport ytdReport = MAPPER.convertValue(body, YTDReport.class);
            assertThat(ytdReport.getNetPay()).isEqualByComparingTo(BigDecimal.valueOf(0));
        });
        // now approve the report
        approvePayroll();
        // Wait and retry to check for updated YTD report
        awaitUpdatedYTDReport(BigDecimal.valueOf(633334.0));
    }

    @Test
    void testApprovePayrollReport() {
        // Assert approved status was pending initially
        assertThat(getReportByCompanyId()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            assertThat(reportResponses.get(0).getPayrollStatus().compareTo(PayrollStatus.PENDING) == 0);
        });

        // Approve
        approvePayroll();

        // Assert approved status is now true
        assertThat(getReportByCompanyId()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            assertThat(reportResponses.get(0).getPayrollStatus().compareTo(PayrollStatus.APPROVED) == 0);
        });
    }

    @Test
    void testApprovedCannotBeRolledBack() {
        // approve the curent payroll
        approvePayroll();
        // send the request again
        webTestClient.post()
                .uri("/compute/payroll")
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(createPayload())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("The payroll for this pay period has already been approved and processed and cannot be altered.");

    }

    @Test
    void testGetAllHeadersForReport() {
        AtomicReference<String> reportId = new AtomicReference<>("");
        assertThat(getReportByCompanyId()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            reportId.set(reportResponses.get(0).getReportId());
        });
        assertThat(getAllHeadersForReport(reportId.get())).isNotNull().satisfies(body -> {
            Set<String> responseSet = MAPPER.convertValue(body, new TypeReference<>() {});
            Set<String> expectedFields = Set.of(
                    "CHARGEABLE INCOME",
                    "Transport Allowance",
                    "Pension Fund",
                    "ANNUAL EMPLOYEE PENSION @ 8%",
                    "Entertainment",
                    "National Housing Fund",
                    "Leave",
                    "ANNUAL CONSOLIDATED ALLOWANCE",
                    "Total Deduction",
                    "Housing Allowance",
                    "StartDate",
                    "Training",
                    "Employer Pension Contribution",
                    "Utility",
                    "Net Pay",
                    "EmployeeId",
                    "RELIEF ALLOWANCE",
                    "Total Employee Pension",
                    "PERSONAL OUTFIT",
                    "VOLUNTARY PENSION CONTRIBUTION",
                    "ANNUAL VOLUNTARY PENSION CONTRIBUTION",
                    "EndDate",
                    "MONTHLY PAYEE",
                    "Basic Salary",
                    "Employee Pension Contribution",
                    "Medical",
                    "Gross Pay",
                    "PayrollType",
                    "GROSS PAY (TAX PURPOSE)",
                    "EmployeeName",
                    "CUSTOM TAX RELIEF APPLICABLE"
            );
            assertThat(responseSet).containsExactlyInAnyOrderElementsOf(expectedFields);
        });
    }

    @Test
    void testGetPaymentElements() {
        AtomicReference<String> reportId = new AtomicReference<>("");
        assertThat(getReportByCompanyId()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            reportId.set(reportResponses.get(0).getReportId());
        });
        assertThat(getPaymentElement(reportId.get())).isNotNull().satisfies(body -> {
            List<Map<String, Object>> responseList = MAPPER.convertValue(body, new TypeReference<>() {});
            assertThat(responseList.size()).isEqualTo(1);
            Map<String, Object> responeMap = responseList.get(0);
            assertThat(responeMap.get(NET_PAY)).isEqualTo(633334.0);
        });
    }

    @Test
    void testGetTotalNetPayByReportId() {
        AtomicReference<String> reportId = new AtomicReference<>("");
        assertThat(getReportByCompanyId()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            reportId.set(reportResponses.get(0).getReportId());
        });
        assertThat(getTotalNetPayByReportId(reportId.get())).isNotNull().satisfies(body -> {
            Map<String, Object> responseMap = MAPPER.convertValue(body, new TypeReference<>() {});
            assertThat(responseMap.get("Total Net Pay")).isEqualTo(633333.27);
            assertThat(responseMap.get("Total Number of Recipients")).isEqualTo(1);
        });
    }

    /****         DASHBOARD CONTROLLER ENDPOINTS      *********/
    @Test
    void testDashboardCardAndCard() {
        // Assert approved status was pending initially
        assertThat(getReportByCompanyId()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            assertThat(reportResponses.get(0).getPayrollStatus().compareTo(PayrollStatus.PENDING) == 0);
        });

        // Approve
        approvePayroll();

        // Assert Dashboard data are updated
        assertThat(getDashboardCard()).isNotNull().satisfies(dashboard -> {
            DashboardCardResponse dashboardCardResponse = MAPPER.convertValue(dashboard, new TypeReference<>() {});
            assertThat(dashboardCardResponse.getTotalOffCyclePayroll()).isEqualTo(0);
            assertThat(dashboardCardResponse.getTotalRegularPayroll()).isEqualTo(1);
            assertThat(dashboardCardResponse.getTotalPayrollCost()).isEqualByComparingTo("633333.27");
            assertThat(dashboardCardResponse.getAverageEmployeeCost()).isEqualByComparingTo("633333.27");
        });

        // Assert Dashboard graph are updated
        assertThat(getDashboardGraph()).isNotNull().satisfies(graph -> {
            Map<String, Object> responseMap = MAPPER.convertValue(graph, new TypeReference<>() {});
            assertThat(responseMap.get("totalOffCyclePayroll")).isEqualTo(0);
            assertThat(responseMap.get("totalRegularPayroll")).isEqualTo(1);
            assertThat(responseMap.get("totalPayrollCost")).isEqualTo(633333.27); //
            assertThat(responseMap.get("averageEmployeeCost")).isEqualTo(633333.27);
        });

        // cancel so the data can be reused for other ITs
        //cancelPayroll();
    }


    private void awaitUpdatedYTDReport(BigDecimal expectedNetPay) {
        int maxAttempts = 10;
        int waitMillis = 400;

        for (int i = 0; i < maxAttempts; i++) {
            YTDReport report = MAPPER.convertValue(getYtdReport(), YTDReport.class);
            if (report.getNetPay().compareTo(expectedNetPay) == 0) {
                //LOGGER.info("Updated YTD Report found: {}", report);
                assertThat(report.getNetPay()).isEqualByComparingTo(expectedNetPay);
                return;
            }
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Test interrupted while waiting for YTD update", e);
            }
        }
        fail("YTDReport was not updated to expected value within timeout");
    }
}