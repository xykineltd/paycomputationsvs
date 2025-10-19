package com.xykine.computation;

import com.xykine.computation.config.TestSecurityConfig;
import com.xykine.computation.domain.JobStatus;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.testdata.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {ComputationApplication.class, TestSecurityConfig.class},webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
public class ComputeIntegTest extends AbstractIntegrationTest {

    @Test
    void testStartReportSummary() throws InterruptedException {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("5000"));
        Map<String, String> startJobResponse =  startReportSummary("2025-06-01", "2025-06-30", true);
        String jobId = startJobResponse.get("jobId");
        JobStatus jobStatus = getStatus(jobId);
        ReportResponse reportResponse = null;
        assertThat(jobStatus.getStatus()).isEqualTo("IN_PROGRESS");
        Thread.sleep(60*1000);
        jobStatus = getStatus(jobId);
        if ("COMPLETED".equalsIgnoreCase(jobStatus.getStatus())) {
            reportResponse = getReportById(jobStatus.getReportId());
        }
        Map<String, Object> body = getReportDetail(reportResponse);
        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(5000);
        });

        startJobResponse =  startReportSummary("2025-06-01", "2025-06-30", false);
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
}
