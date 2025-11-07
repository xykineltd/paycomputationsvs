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
}
