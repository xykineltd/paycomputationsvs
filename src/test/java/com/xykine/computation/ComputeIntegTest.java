package com.xykine.computation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xykine.computation.config.TestSecurityConfig;
import com.xykine.computation.domain.JobStatus;
import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.request.UpdatePayrollStatusRequest;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.testdata.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.xykine.payroll.model.PaymentInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {ComputationApplication.class, TestSecurityConfig.class},webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
public class ComputeIntegTest extends AbstractIntegrationTest {

    private static final long WAITTIME = 2*60*1000;

    @Test
    void testStartReportSummary() throws InterruptedException {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("5000"));
        Map<String, String> startJobResponse =  startReportSummary("2025-06-01", "2025-06-30", true, null);
        String jobId = startJobResponse.get("jobId");
        JobStatus jobStatus = getStatus(jobId);
        ReportResponse reportResponse = null;
        assertThat(jobStatus.getStatus()).isEqualTo("IN_PROGRESS");
        Thread.sleep(2*60*1000);
        jobStatus = getStatus(jobId);
        if ("COMPLETED".equalsIgnoreCase(jobStatus.getStatus())) {
            reportResponse = getReportById(jobStatus.getReportId());
        }
        Map<String, Object> body = getReportDetail(reportResponse);
        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(5000);
        });

        startJobResponse =  startReportSummary("2025-06-01", "2025-06-30", false, null);
        jobId = startJobResponse.get("jobId");
        jobStatus = getStatus(jobId);
        if ("COMPLETED".equalsIgnoreCase(jobStatus.getStatus())) {
            reportResponse = getReportById(jobStatus.getReportId());
        }
        body = getReportDetail(reportResponse);
        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(5000);
        });
    }

    @Test
    void testGetVarianceDetails() throws InterruptedException {
        when(adminService.getPaymentInfoList(any(), anyString()))
                .thenReturn(
                        TestDataFactory.getPaymentSettings("contract-staff-2000"),   // 1st call
                        TestDataFactory.getPaymentSettings("contract-staff-2000-2-days-absent")  // 2nd call
                );
        Map<String, String> startJobResponse = startReportSummary("2025-05-01", "2025-05-30", true, null);
        String jobId = startJobResponse.get("jobId");
        Thread.sleep(WAITTIME);
        JobStatus jobStatus = getStatus(jobId);
        String reportId = "";
        if ("COMPLETED".equalsIgnoreCase(jobStatus.getStatus())) {
            reportId = jobStatus.getReportId();
        }
        startReportSummary("2025-05-01", "2025-05-30", false, null);
        Thread.sleep(WAITTIME);
        System.out.println(" =====> report " + reportId);
        UpdatePayrollStatusRequest updateReportRequest = UpdatePayrollStatusRequest.builder()
                .reportId(UUID.fromString(reportId))
                .status(PayrollStatus.APPROVED)
                .companyId("682cf69492b07e60fa109911")
                .build();

        approvePayroll(updateReportRequest);

        updateReportRequest = UpdatePayrollStatusRequest.builder()
                .reportId(UUID.fromString(reportId))
                .status(PayrollStatus.COMPLETED)
                .companyId("682cf69492b07e60fa109911")
                .build();

        approvePayroll(updateReportRequest);

        ReportResponse response = getReportById(reportId);
        Map<String, Object> body = getReportDetail(response);
        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(2000);
        });
        List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });
        PaymentInfo paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo.getYtdReport()).isNotNull().satisfies((x) -> {
            assertThat(x.get("WHT").compareTo(BigDecimal.valueOf(7500).multiply(BigDecimal.valueOf(2000))));
            assertThat(x.get("Net Pay").compareTo(BigDecimal.valueOf(142500).multiply(BigDecimal.valueOf(2000))));
            assertThat(x.get("Taxable Income").compareTo(BigDecimal.valueOf(150000).multiply(BigDecimal.valueOf(2000))));
        });
        reportId = "";
        startJobResponse =  startReportSummary("2025-06-01", "2025-06-30", true, null);
        Thread.sleep(WAITTIME);
        jobId = startJobResponse.get("jobId");
        jobStatus = getStatus(jobId);
        if ("COMPLETED".equalsIgnoreCase(jobStatus.getStatus())) {
            reportId = jobStatus.getReportId();
        }
        startReportSummary("2025-06-01", "2025-06-30", false, null);
        Thread.sleep(WAITTIME);

        updateReportRequest = UpdatePayrollStatusRequest.builder()
                .reportId(UUID.fromString(reportId))
                .status(PayrollStatus.APPROVED)
                .companyId("682cf69492b07e60fa109911")
                .build();

        approvePayroll(updateReportRequest);

        response = getReportById(reportId);
        body = getReportDetail(response);
        reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });
        paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo.getYtdReport()).isNotNull().satisfies((x) -> {
            assertThat(x.get("WHT").compareTo(BigDecimal.valueOf(7500).multiply(BigDecimal.valueOf(2000))));
            assertThat(x.get("Net Pay").compareTo(BigDecimal.valueOf(142500).multiply(BigDecimal.valueOf(2000))));
            assertThat(x.get("Taxable Income").compareTo(BigDecimal.valueOf(150000).multiply(BigDecimal.valueOf(2000))));
        });

        when(adminService.getEmployeeIdListForFilter(any(), anyString())).thenReturn(List.of("emp-10"));
        Map<String, Object> summaryDetails = getVarianceDetails(reportId, "Total Gross Pay");

        assertThat(summaryDetails)
                .isNotNull()
                .containsKey("Total Gross Pay");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) summaryDetails.get("Total Gross Pay");

        assertThat(details)
                .isNotEmpty()
                .first()
                .satisfies(detailMap -> {
                    BigDecimal variance = new BigDecimal(detailMap.get("variance").toString());
                    BigDecimal value = new BigDecimal(detailMap.get("value").toString());
                    assertThat(variance).isEqualByComparingTo(BigDecimal.valueOf(-14285.72));
                    assertThat(value).isEqualByComparingTo(BigDecimal.valueOf(135714.28));
                });
    }

    @Test   // running now
    void testStandardWithPerformanceBonusComputeWithPaymentDistributionList() throws InterruptedException {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("standard and performance with payment distribution list"));
        Map<String, String> startJobResponse =  startReportSummary("2025-06-01", "2025-06-30", true, null);
        String jobId = startJobResponse.get("jobId");

        Thread.sleep(WAITTIME);
        JobStatus jobStatus = getStatus(jobId);
        ReportResponse reportResponse = null;

        LOGGER.info(" ===> the current status {} ", jobStatus.getStatus());

        if ("COMPLETED".equalsIgnoreCase(jobStatus.getStatus())) {
            reportResponse = getReportById(jobStatus.getReportId());
        }
        Map<String, Object> body = getReportDetail(reportResponse);
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
            assertThat(x.get("Monthly Performance Bonus")).isEqualByComparingTo(BigDecimal.valueOf(117074.31));
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
            assertThat(x.get("Annual Voluntary Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("ANNUAL CONSOLIDATED ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2215841.58));
            assertThat(x.get("Variable Consolidated Relief Allowance")).isEqualByComparingTo(BigDecimal.valueOf(35178.32));
            assertThat(x.get("GROSS PAY (TAX PURPOSE)")).isEqualByComparingTo(BigDecimal.valueOf(10079207.92));
            assertThat(x.get("RELIEF ALLOWANCE")).isEqualByComparingTo(BigDecimal.valueOf(2488467.48));
        });

        Map<String, BigDecimal> deduction = paymentInfo.getDeduction();
        assertThat(deduction).isNotNull().satisfies((x) -> {
            assertThat(x.get("Monthly Paye")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("Voluntary Pension Contribution")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(x.get("Pension Fund")).isEqualByComparingTo(BigDecimal.valueOf(20555.13));
            assertThat(x.get("Paye Tax on OVERTIME GROSS")).isEqualByComparingTo(BigDecimal.valueOf(5822.59));
            assertThat(x.get("Paye Tax on Monthly Performance Bonus")).isEqualByComparingTo(BigDecimal.valueOf(15577.46));
            assertThat(x.get("National Housing Fund")).isEqualByComparingTo(BigDecimal.valueOf(0.0));
            assertThat(x.get("Company Car Loan")).isEqualByComparingTo(BigDecimal.valueOf(10000));  // Personal Deduction
            assertThat(x.get("Total Deduction")).isEqualByComparingTo(BigDecimal.valueOf(178562.12));
        });

        Map<String, BigDecimal> payeeTax = paymentInfo.getPayeeTax();
        assertThat(payeeTax).isNotNull().satisfies((x) -> {
            assertThat(x.get("Monthly Paye")).isEqualByComparingTo(BigDecimal.valueOf(126606.94));
            assertThat(x.get("Taxable Income")).isEqualByComparingTo(BigDecimal.valueOf(7863366.34));
            assertThat(x.get("Paye Tax on OVERTIME GROSS")).isEqualByComparingTo(BigDecimal.valueOf(5822.59));
            assertThat(x.get("Paye Tax on Monthly Performance Bonus")).isEqualByComparingTo(BigDecimal.valueOf(15577.46));
            assertThat(x.get("ANNUAL PAYE TAX")).isEqualByComparingTo(BigDecimal.valueOf(1679207.92));
        });
    }


}
