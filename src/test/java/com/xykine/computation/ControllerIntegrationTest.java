package com.xykine.computation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xykine.computation.config.TestSecurityConfig;
import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.repo.YTDReportRepo;
import com.xykine.computation.response.DashboardCardResponse;
import com.xykine.computation.response.ReportResponse;

import com.xykine.computation.testdata.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.xykine.payroll.model.MapKeys.NET_PAY;

@SpringBootTest(classes = {ComputationApplication.class, TestSecurityConfig.class},webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
public class ControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private YTDReportRepo ytdReportRepo;

    @BeforeEach
    void setupReportTestData() {
        when(adminService.getPaymentInfoList(any(), anyString()))
                .thenReturn(TestDataFactory.getPaymentSettings("standard"));
        getReportSummary();
    }

    @AfterEach
    void cancelReport() {
        // cancel so the data can be reused for other ITs
        cancelPayroll();
    }

    /****        COMPUTE CONTROLLER ENDPOINTS      *********/

    @Test
    void testStandardCompute()  {
        //when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("standard"));
        ReportResponse reportSummary = getReportSummary();
        assert reportSummary != null;
        LOGGER.debug(" standard summary ====> {}", reportSummary.getSummary());

        assertThat(reportSummary).isNotNull();
        assertThat(reportSummary.getSummary()).isNotNull();
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_GROSS_PAY)).isEqualByComparingTo("653052.07");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_NHF)).isEqualByComparingTo("12693.07");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_PERSONAL_DEDUCTION)).isEqualByComparingTo("6031.32");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_NET_PAY)).isEqualByComparingTo("491030.10");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION)).isEqualByComparingTo("64483.26");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION)).isEqualByComparingTo("51586.61");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_PAYEE_TAX)).isEqualByComparingTo("91710.97");

        Map<String, Object> body = getReportDetail(reportSummary);

        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(1);
        });

        List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });

        PaymentInfo paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo).isNotNull().satisfies((x) -> {
            assertThat(x.getNetPay()).isEqualByComparingTo(BigDecimal.valueOf(491030.1));
        });

        Map<String, BigDecimal> grossPay = paymentInfo.getGrossPay();
        assertThat(grossPay).isNotNull().satisfies((x) -> {
            assertThat(x.get("Transport Allowance")).isEqualByComparingTo(BigDecimal.valueOf(50770.89));
            assertThat(x.get("Housing Allowance")).isEqualByComparingTo(BigDecimal.valueOf(86338.91));
            assertThat(x.get("Acting Allowance")).isEqualByComparingTo(BigDecimal.valueOf(8219.55));
            assertThat(x.get("Basic Salary")).isEqualByComparingTo(BigDecimal.valueOf(507722.72));
            assertThat(x.get("Gross Pay")).isEqualByComparingTo(BigDecimal.valueOf(653052.07));
        });

        Map<String, BigDecimal> taxRelief = paymentInfo.getTaxRelief();
        assertThat(taxRelief).isNotNull().satisfies((x) -> {
            assertThat(x.get("Fixed Consolidated Relief Allowance")).isEqualByComparingTo(BigDecimal.valueOf(16666.67));
            assertThat(x.get("National Housing Fund")).isEqualByComparingTo(BigDecimal.valueOf(12693.07));
            assertThat(x.get("Variable Consolidated Relief Allowance")).isEqualByComparingTo(BigDecimal.valueOf(117754.48));
            assertThat(x.get("Total Tax Relief")).isEqualByComparingTo(BigDecimal.valueOf(198700.83));

        });

        Map<String, BigDecimal> payeeTax = paymentInfo.getPayeeTax();
        assertThat(payeeTax).isNotNull().satisfies((x) -> {
            assertThat(x.get("Taxable Income")).isEqualByComparingTo(BigDecimal.valueOf(454351.24));
            assertThat(x.get("Payee Tax")).isEqualByComparingTo(BigDecimal.valueOf(91710.97));

        });

        Map<String, BigDecimal> pension = paymentInfo.getPension();
        assertThat(pension).isNotNull().satisfies((x) -> {
            assertThat(x.get("Employer Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(64483.26));
            assertThat(x.get("Employee Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(51586.61));
            assertThat(x.get("Total Employee Pension")).isEqualByComparingTo(BigDecimal.valueOf(116069.87));

        });
    }

    @Test
    void testOffCycleCompute()  {
        when(adminService.getPaymentInfoList(any(), anyString())).thenReturn(TestDataFactory.getPaymentSettings("off-cycle"));
        ReportResponse reportSummary = getReportSummary();
        assert reportSummary != null;

        LOGGER.debug(" off-cycle summary ====> {}", reportSummary.getSummary());

        assertThat(reportSummary).isNotNull();
        assertThat(reportSummary.getSummary()).isNotNull();
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_GROSS_PAY)).isEqualByComparingTo("507722.72");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_NHF)).isEqualByComparingTo("0");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_PERSONAL_DEDUCTION)).isEqualByComparingTo("0");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_NET_PAY)).isEqualByComparingTo("473758.63");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION)).isEqualByComparingTo("0");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION)).isEqualByComparingTo("0");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_PAYEE_TAX)).isEqualByComparingTo("33964.09");

        Map<String, Object> body = getReportDetail(reportSummary);

        assertThat(body).isNotNull().satisfies((x) -> {
            assertThat(x.get("totalItems")).isEqualTo(1);
        });

        List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<List<ReportResponse>>() {
        });

        PaymentInfo paymentInfo = reportResponses.get(0).getDetail().getReport();
        assertThat(paymentInfo).isNotNull().satisfies((x) -> {
            assertThat(x.getNetPay()).isEqualByComparingTo(BigDecimal.valueOf(473758.63));
        });

        Map<String, BigDecimal> grossPay = paymentInfo.getGrossPay();
        assertThat(grossPay).isNotNull().satisfies((x) -> {
            assertThat(x.get("Gross Pay")).isEqualByComparingTo(BigDecimal.valueOf(507722.72));
        });

        Map<String, BigDecimal> taxRelief = paymentInfo.getTaxRelief();
        assertThat(taxRelief).isNotNull().satisfies((x) -> {
            assertThat(x.get("Fixed Consolidated Relief Allowance")).isEqualByComparingTo(BigDecimal.valueOf(200000.0));
            assertThat(x.get("Variable Consolidated Relief Allowance")).isEqualByComparingTo(BigDecimal.valueOf(101544.55));
            assertThat(x.get("Total Tax Relief")).isEqualByComparingTo(BigDecimal.valueOf(301544.55));
        });

        Map<String, BigDecimal> payeeTax = paymentInfo.getPayeeTax();
        assertThat(payeeTax).isNotNull().satisfies((x) -> {
            assertThat(x.get("Taxable Income")).isEqualByComparingTo(BigDecimal.valueOf(206178.17));
            assertThat(x.get("Payee Tax")).isEqualByComparingTo(BigDecimal.valueOf(33964.09));

        });

        Map<String, BigDecimal> pension = paymentInfo.getPension();
        assertThat(pension).isNotNull().satisfies((x) -> {
            assertThat(x.get("Employer Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(0));
            assertThat(x.get("Employee Pension Contribution")).isEqualByComparingTo(BigDecimal.valueOf(0));
        });
    }

    /****         REPORT CONTROLLER ENDPOINTS      *********/

    @Test
    void testGetReportByCompanyIdAndStatusAndVerifyApproveStatusIsFalse() {
        assertThat(getReportByCompanyIdAndStatus()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            assertThat(reportResponses.get(0).isPayrollApproved()).isFalse();
        });
    }

    @Test
    void testGetReportByCompanyIdAndEmployeeId() {
        assertThat(getReportByCompanyIdAndEmployeeId()).isNotNull().satisfies(body -> {
            List<ReportResponse> reportResponses = MAPPER.convertValue(body.get("payrollDetails"), new TypeReference<>() {});
            assertThat(reportResponses.get(0).getEmployeeId()).isEqualTo(TestDataFactory.TEST_EMPLOYEE_ID);
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
            assertThat(body.getEmployeeId()).isEqualTo(TestDataFactory.TEST_EMPLOYEE_ID);
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
        awaitUpdatedYTDReport(BigDecimal.valueOf(491030.1));
    }

    @Test
    void testApprovePayrollReport() {
        // Assert approved status was false initially
        assertThat(getReportByCompanyId()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            assertThat(reportResponses.get(0).isPayrollApproved()).isFalse();
        });

        // Approve
        approvePayroll();

        // Assert approved status is now true
        assertThat(getReportByCompanyId()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            assertThat(reportResponses.get(0).isPayrollApproved()).isTrue();
        });
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
                    "Transport Allowance",
                    "Fixed Consolidated Relief Allowance",
                    "Total Employee Pension",
                    "Pension Fund",
                    "Acting Allowance",
                    "Coop Loan",
                    "National Housing Fund",
                    "Payee Tax",
                    "Total Deduction",
                    "EndDate",
                    "Housing Allowance",
                    "StartDate",
                    "Employer Pension Contribution",
                    "Net Pay",
                    "Total Tax Relief",
                    "Basic Salary",
                    "Employee Pension Contribution",
                    "Gross Pay",
                    "EmployeeId",
                    "PayrollType",
                    "Variable Consolidated Relief Allowance",
                    "EmployeeName"
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
            assertThat(responeMap.get(NET_PAY)).isEqualTo(491030.1);
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
            assertThat(responseMap.get("Total Net Pay")).isEqualTo(491030.1);
            assertThat(responseMap.get("Total Number of Recipients")).isEqualTo(1);
        });
    }

    /****         DASHBOARD CONTROLLER ENDPOINTS      *********/
    @Test
    void testDashboardCardAndCard() {
        // Assert approved status was false initially
        assertThat(getReportByCompanyId()).isNotNull().satisfies(reportResponses -> {
            assertThat(reportResponses.size()).isEqualTo(1);
            assertThat(reportResponses.get(0).isPayrollApproved()).isFalse();
        });

        // Approve
        approvePayroll();

        // Assert Dashboard data are updated
        assertThat(getDashboardCard()).isNotNull().satisfies(dashboard -> {
            DashboardCardResponse dashboardCardResponse = MAPPER.convertValue(dashboard, new TypeReference<>() {});
            assertThat(dashboardCardResponse.getTotalOffCyclePayroll()).isEqualTo(0);
            assertThat(dashboardCardResponse.getTotalRegularPayroll()).isEqualTo(1);
            assertThat(dashboardCardResponse.getTotalPayrollCost()).isEqualByComparingTo("491030.1");
            assertThat(dashboardCardResponse.getAverageEmployeeCost()).isEqualByComparingTo("491030.1");
        });

        // Assert Dashboard graph are updated
        assertThat(getDashboardGraph()).isNotNull().satisfies(graph -> {
            Map<String, Object> responseMap = MAPPER.convertValue(graph, new TypeReference<>() {});
            assertThat(responseMap.get("totalOffCyclePayroll")).isEqualTo(0);
            assertThat(responseMap.get("totalRegularPayroll")).isEqualTo(1);
            assertThat(responseMap.get("totalPayrollCost")).isEqualTo(491030.1); //
            assertThat(responseMap.get("averageEmployeeCost")).isEqualTo(491030.1);
        });

        // cancel so the data can be reused for other ITs
        cancelPayroll();
    }


    private void awaitUpdatedYTDReport(BigDecimal expectedNetPay) {
        int maxAttempts = 10;
        int waitMillis = 500;

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