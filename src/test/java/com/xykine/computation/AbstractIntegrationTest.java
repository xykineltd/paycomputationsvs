package com.xykine.computation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.request.RetrievePaymentElementPayload;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.AdminService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.xykine.computation.testdata.TestDataFactory.TEST_COMPANY_ID;
import static com.xykine.computation.testdata.TestDataFactory.TEST_EMPLOYEE_ID;
import static org.xykine.payroll.model.MapKeys.NET_PAY;

@Testcontainers
@TestPropertySource(properties = {"spring.profiles.active=test"})
public abstract class AbstractIntegrationTest {


    @MockBean
    protected AdminService adminService;

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected Jwt jwt;

    @LocalServerPort
    protected int port;

    protected static ObjectMapper MAPPER = new ObjectMapper();

    protected static final Logger LOGGER = LoggerFactory.getLogger(ControllerIntegrationTest.class);

    @Container
    protected static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0");

    @Container
    protected static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.0")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    protected  PaymentInfoRequest createPayload() {
        PaymentInfoRequest paymentInfoRequest = new PaymentInfoRequest();
        paymentInfoRequest.setCompanyId("682cf69492b07e60fa109911");
        paymentInfoRequest.setPayrollSimulation(false);
        paymentInfoRequest.setStart(LocalDate.now());
        paymentInfoRequest.setEnd(LocalDate.now().plusDays(30));
        return paymentInfoRequest;
    }

    ReportResponse getReportSummary() {
        return webTestClient.post()
                .uri("/compute/payroll")
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(createPayload())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReportResponse.class)
                .returnResult()
                .getResponseBody();
    }

    protected Map<String, Object> getReport(String url){
        return webTestClient.get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();
    }

    protected List<ReportResponse> getReportAsList(String url){
        return webTestClient.get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<ReportResponse>>() {})
                .returnResult()
                .getResponseBody();
    }

    ReportResponse getReportDirect(String url) {
        return webTestClient.get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReportResponse.class)
                .returnResult()
                .getResponseBody();
    }

    void approveReport(String url, UpdateReportRequest request) {
        webTestClient.put()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Void.class)
                .returnResult()
                .getResponseBody();
    }

    Object getPaymentElement(String url, RetrievePaymentElementPayload request) {
        return webTestClient.post()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Object.class)
                .returnResult()
                .getResponseBody();
    }

    Object getReportGenericDirect(String url) {
        return webTestClient.get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Object.class)
                .returnResult()
                .getResponseBody();
    }

    protected Map<String, Object> getReportDetail(ReportResponse reportSummary) {
        String url = UriComponentsBuilder.fromHttpUrl("http://localhost:" + port + "/compute/reports/paymentDetails")
                .queryParam("id", reportSummary.getReportId())
                .queryParam("companyId", reportSummary.getCompanyId())
                .queryParam("page", "0")
                .queryParam("size", "3")
                .toUriString();
        return getReport(url);
    }

    List<ReportResponse> getReportByCompanyId() {
        // @GetMapping("/{companyId}/")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + TEST_COMPANY_ID + "/")
                .toUriString();
        return getReportAsList(url);
    }

    List<ReportResponse> getReportByCompanyIdAndStatus() {
        // @GetMapping("/{companyId}/status/{status}")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + TEST_COMPANY_ID + "/status/PENDING")
                .toUriString();
        return getReportAsList(url);
    }

    Map<String, Object> getReportByCompanyIdAndEmployeeId() {
        //@GetMapping("/{companyId}/{employeeId}")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + TEST_COMPANY_ID + "/" + TEST_EMPLOYEE_ID)
                .toUriString();
        return getReport(url);
    }

    ReportResponse getReportByStartDateAndCompanyId() {
        // @GetMapping("/get-by-start-date/{companyId}/{startDate}")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "get-by-start-date/" + TEST_COMPANY_ID + "/" + LocalDate.now())
                .toUriString();
        return getReportDirect(url);
    }

    void approvePayroll() {
        // @PutMapping("/approve")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";

        UpdateReportRequest updateReportRequest = new UpdateReportRequest();
        updateReportRequest.setStartDate(LocalDate.now().toString());
        updateReportRequest.setCompanyId(TEST_COMPANY_ID);
        updateReportRequest.setPayrollApproved(true);

        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "approve").toUriString();
        approveReport(url, updateReportRequest);
    }

    void cancelPayroll() {
        // @PutMapping("/cancel")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";

        UpdateReportRequest updateReportRequest = new UpdateReportRequest();
        updateReportRequest.setStartDate(LocalDate.now().toString());
        updateReportRequest.setCompanyId(TEST_COMPANY_ID);
        updateReportRequest.setCancelPayroll(true);

        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "approve").toUriString();
        approveReport(url, updateReportRequest);
    }


    ReportResponse getPaymentDetailsByEmployeeAndCompanyId() {
        //  @GetMapping("/paymentDetails/get-by-employee")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "paymentDetails/get-by-employee")
                .queryParam("employeeId", TEST_EMPLOYEE_ID)
                .queryParam("startDate", LocalDate.now())
                .queryParam("companyId", TEST_COMPANY_ID)
                .toUriString();
        return getReportDirect(url);
    }

    Object getYtdReport() {
        // @GetMapping("/ytdReport")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "ytdReport")
                .queryParam("employeeId", TEST_EMPLOYEE_ID)
                .queryParam("companyId", TEST_COMPANY_ID)
                .toUriString();
        return getReportGenericDirect(url);
    }

    Object getPaymentElement(String reportId) {
        // @PostMapping("/retrieve-payment-element")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "retrieve-payment-element")
                .toUriString();

        RetrievePaymentElementPayload payload = new RetrievePaymentElementPayload();
        List<String> selectedHeader = new ArrayList<>();
        selectedHeader.add(NET_PAY);
        payload.setSelectedHeader(selectedHeader);
        payload.setCompanyId(TEST_COMPANY_ID);
        payload.setReportId(reportId);

        return getPaymentElement(url, payload);
    }

    Object getAllHeadersForReport(String reportId) {
        // @GetMapping("/payment-header-options/company-id/{companyID}/report-id/{reportId}")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "payment-header-options/company-id/" + TEST_COMPANY_ID  + "/report-id/" + reportId)
                .toUriString();
        return getReportGenericDirect(url);
    }

    Object getTotalNetPayByReportId(String reportId) {
        //  @PostMapping("/total-netpay-by-report-id")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "total-netpay-by-report-id")
                .toUriString();

        RetrievePaymentElementPayload request = new RetrievePaymentElementPayload();
        request.setCompanyId(TEST_COMPANY_ID);
        request.setReportId(reportId);

        return getPaymentElement(url, request);
    }

    Object getDashboardCard() {
        //  @GetMapping("/card")
        String URL_PREFIX = "http://localhost:" + port + "/compute/dashboard/card";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX )
                .queryParam("companyId", TEST_COMPANY_ID)
                .toUriString();
        return getReportGenericDirect(url);
    }

    Object getDashboardGraph() {
        //  @GetMapping("/graph")
        String URL_PREFIX = "http://localhost:" + port + "/compute/dashboard/card";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX )
                .queryParam("companyId", TEST_COMPANY_ID)
                .toUriString();
        return getReportGenericDirect(url);
    }

}