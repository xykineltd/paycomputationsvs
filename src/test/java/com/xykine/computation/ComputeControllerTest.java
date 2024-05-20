package com.xykine.computation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.model.MapKeys;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.ComputeService;
import com.xykine.computation.service.ReportPersistenceService;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ComputeControllerTest {
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ReportPersistenceService reportPersistenceService;
    @Autowired
    private  ComputeService computeService;
    @Autowired
    private  ComputationConstantsRepo computationConstantsRepo;
    @Autowired
    private  TaxRepo taxRepo;

    private static Process process;

    @LocalServerPort
    private int port;

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeControllerTest.class);
    @Test
    void testComputePayroll() throws Exception {

        ReportResponse reportSummary =
                restTemplate.postForEntity("http://localhost:" + port + "/compute/payroll", createPayload(), ReportResponse.class).getBody();

        assertThat(reportSummary).isNotNull();
        assertThat(reportSummary.getSummary()).isNotNull();
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_GROSS_PAY)).isEqualByComparingTo("4895833.40");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_NHF)).isEqualByComparingTo("52083.40");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_PERSONAL_DEDUCTION)).isEqualByComparingTo("150000");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_NET_PAY)).isEqualByComparingTo("3701016.60");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION)).isEqualByComparingTo("427083.40");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION)).isEqualByComparingTo("341666.70");
        assertThat(reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_PAYEE_TAX)).isEqualByComparingTo("651066.70");

        UriComponents builder = UriComponentsBuilder.fromHttpUrl("http://localhost:" + port + "/compute/reports/paymentDetails")
                .queryParam("id",reportSummary.getReportId())
                .queryParam("companyId",reportSummary.getCompanyId())
                .queryParam("page","0")
                .queryParam("size","3")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");

        HttpEntity<String> requestEntity = new HttpEntity<>(null, headers);
        ResponseEntity<Map> reportDetail = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, requestEntity, Map.class);

        assertThat(reportDetail).isNotNull();

        Map<String, Object> body = reportDetail.getBody();
        assertThat(body).isNotNull().satisfies((x) -> {
           x.get("totalItems").equals("10");
        });

        ObjectMapper mapper = new ObjectMapper();
        List<ReportResponse> reportResponses = mapper.convertValue( body.get("payrollDetails"),  new TypeReference<List<ReportResponse>>() {}
        );

        PaymentInfo paymentInfo =  reportResponses.get(0).getDetail().getReport();

        assertThat(paymentInfo).isNotNull().satisfies((x) -> {
           x.getNetPay().equals(BigDecimal.valueOf(370101.66 ));
        });
        // Assert Gross Pay
        Map<String, BigDecimal> grossPay = paymentInfo.getGrossPay();
        assertThat(grossPay).isNotNull().satisfies((x) -> {
            x.get("TRANSPORT ALLOWANCE").equals(BigDecimal.valueOf(93750));
            x.get("HOUSING ALLOWANCE").equals(BigDecimal.valueOf(125000));
            x.get("Basic Salary").equals(BigDecimal.valueOf(208333.34));
            x.get("RELOCATION ALLOWANCE").equals(BigDecimal.valueOf(62500));
            x.get("Gross Pay").equals(BigDecimal.valueOf(489583.34));
        });
        // Assert Tax Relief
        Map<String, BigDecimal> taxRelief = paymentInfo.getTaxRelief();
        assertThat(taxRelief).isNotNull().satisfies((x) -> {
            x.get("Fixed Consolidated Relief Allowance").equals(BigDecimal.valueOf(16666.67));
            x.get("Employee Pension Contribution").equals(BigDecimal.valueOf(34166.67));
            x.get("National Housing Fund").equals(BigDecimal.valueOf(5208.34));
            x.get("Total Tax Relief").equals(BigDecimal.valueOf(146083.35));
        });

        // Assert Payee Tax
        Map<String, BigDecimal> payeeTax = paymentInfo.getPayeeTax();
        assertThat(payeeTax).isNotNull().satisfies((x) -> {
            x.get("Taxable Income").equals(BigDecimal.valueOf(343499.99));
            x.get("Payee Tax").equals(BigDecimal.valueOf(65106.67));
        });

        // Assert Pension
        Map<String, BigDecimal> pension  = paymentInfo.getPension();
        assertThat(pension).isNotNull().satisfies((x) -> {
            x.get("Employer Pension Contribution").equals(BigDecimal.valueOf(42708.34));
            x.get("Employee Pension Contribution").equals(BigDecimal.valueOf(34166.67));
            x.get("Total Employee Pension").equals(BigDecimal.valueOf(76875.01));
        });
    }

    PaymentInfoRequest createPayload() {
        PaymentInfoRequest paymentInfoRequest = new PaymentInfoRequest();
        paymentInfoRequest.setCompanyId("12345");
        paymentInfoRequest.setPayrollSimulation(false);
        paymentInfoRequest.setStart(LocalDate.now());
        paymentInfoRequest.setEnd(LocalDate.now().plusDays(30));
        return paymentInfoRequest;
    }
}

