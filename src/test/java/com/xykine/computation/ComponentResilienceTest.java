package com.xykine.computation;

import com.xykine.computation.config.TestSecurityConfig;
import com.xykine.computation.testdata.TestDataFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.xykine.computation.testdata.TestDataFactory.TEST_EMPLOYEE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {ComputationApplication.class, TestSecurityConfig.class},webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
public class ComponentResilienceTest extends AbstractIntegrationTest {

    static List<Long> timeTaken = new ArrayList<>();

    @BeforeEach
    void setUp() {
        webTestClient = webTestClient
                .mutate()
                .responseTimeout(Duration.ofSeconds(3000)) // 👈 increase timeout here
                .build();
    }

    @AfterEach
    void cancelReport() {
        // cancel so the data can be reused for other ITs
        payrollReportDetailRepo.deleteAll();
        payrollReportSummaryRepo.deleteAll();
    }


    //@Test
    void testGetReportByCompanyId() {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("ten-entries"));
        getReportSummary();
        assertThat(getReportByCompanyId()).isNotNull().satisfies(x -> {
            assertThat(x.size()).isEqualTo(1);
        });
    }

    //@Test
    void testGetReportByCompanyIdForOneThousandEntries() {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("one-thousand-entries"));
        long start = System.nanoTime();
        getReportSummary();
        var report = getReportByCompanyId();
        long durationInMillis = (System.nanoTime() - start) / 1_000_000;
        LOGGER.info("Report generation for 1000 entries took {} ms", durationInMillis);
        assertThat(report).isNotNull().satisfies(x -> {
            assertThat(x.size()).isEqualTo(1);
        });
    }

   //@Test
    void testGetReportByCompanyIdForTwoThousandEntries() throws InterruptedException {
       // for (int i = 0; i < 10; i++) {
            when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("two-thousand-entries"));
            long start = System.nanoTime();
            getReportSummary();
            var report = getReportByCompanyId();
            long durationInMillis = (System.nanoTime() - start) / 1_000_000;
            LOGGER.info("Report generation for 2000 entries took {} ms", durationInMillis);
            timeTaken.add(durationInMillis);
            assertThat(report).isNotNull().satisfies(x -> {
                assertThat(x.size()).isEqualTo(1);
            });
        //    Thread.sleep(1000);
        }
//       for (long x : timeTaken) {
//           System.out.println(" ==> " + x);
//       }
//    }

   // @Test
    void testGetReportByCompanyIdForFiveThousandEntries() throws InterruptedException {
        // for (int i = 0; i < 10; i++) {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("5000"));
        long start = System.nanoTime();
        getReportSummary();
        var report = getReportByCompanyId();
        long durationInMillis = (System.nanoTime() - start) / 1_000_000;
        LOGGER.info("Report generation for 2000 entries took {} ms", durationInMillis);
        timeTaken.add(durationInMillis);
        assertThat(report).isNotNull().satisfies(x -> {
            assertThat(x.size()).isEqualTo(1);
        });
        //    Thread.sleep(1000);
    }
//       for (long x : timeTaken) {
//           System.out.println(" ==> " + x);
//       }
//    }

    /*.     To test for consistency. Always run locally only.
    @Test
    void runRepeatedTestGetReportByCompanyId() throws InterruptedException {
        int passCount = 0;
        int failCount = 0;
        for (int i = 0; i < 50; i++) {
            try {
                System.out.println("🔁 Run #" + (i + 1));
                testGetReportByCompanyId();  // This is your test method
                passCount++;
            } catch (Throwable t) {
                failCount++;
                System.err.println("❌ Run #" + (i + 1) + " failed: " + t.getMessage());
                t.printStackTrace();
            }
            if (i < 49) {
                Thread.sleep(15000); // 15 seconds
            }
        }
        System.out.println("✅ Total Passed: " + passCount);
        System.out.println("❌ Total Failed: " + failCount);
    }
     */
}
