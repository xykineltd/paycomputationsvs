package com.xykine.computation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.domain.JobStatus;
import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import com.xykine.computation.request.*;
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
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.PaymentSettingsResponse;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

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

    @Autowired
    protected PayrollReportDetailRepo payrollReportDetailRepo;

    @Autowired
    protected PayrollReportSummaryRepo payrollReportSummaryRepo;

    protected static ObjectMapper MAPPER = new ObjectMapper();

    protected static final Logger LOGGER = LoggerFactory.getLogger(ControllerIntegrationTest.class);

    @Container
    protected static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Container
    protected static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.0")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    protected  PaymentInfoRequest createPayload(String startDate, String endDate) {
        PaymentInfoRequest paymentInfoRequest = new PaymentInfoRequest();
        if (startDate == null || endDate == null) {
            paymentInfoRequest.setStart(LocalDate.now());
            paymentInfoRequest.setEnd(LocalDate.now().plusDays(30));
        } else {
            paymentInfoRequest.setStart(LocalDate.parse(startDate));
            paymentInfoRequest.setEnd(LocalDate.parse(endDate));
        }
        paymentInfoRequest.setCompanyId(TEST_COMPANY_ID);
        paymentInfoRequest.setPayrollSimulation(false);
        return paymentInfoRequest;
    }

    protected  PaymentInfoRequest createPayload(String startDate, String endDate, boolean payrollSimulation) {
        PaymentInfoRequest paymentInfoRequest = new PaymentInfoRequest();
        if (startDate == null || endDate == null) {
            paymentInfoRequest.setStart(LocalDate.now());
            paymentInfoRequest.setEnd(LocalDate.now().plusDays(30));
        } else {
            paymentInfoRequest.setStart(LocalDate.parse(startDate));
            paymentInfoRequest.setEnd(LocalDate.parse(endDate));
        }
        paymentInfoRequest.setCompanyId(TEST_COMPANY_ID);
        paymentInfoRequest.setPayrollSimulation(payrollSimulation);
        return paymentInfoRequest;
    }

    protected  PaymentInfoRequest customCreatePayload(String companyId) {
        PaymentInfoRequest paymentInfoRequest = new PaymentInfoRequest();
        paymentInfoRequest.setCompanyId(companyId);
        paymentInfoRequest.setPayrollSimulation(false);
        paymentInfoRequest.setStart(LocalDate.parse("2025-06-01"));
        paymentInfoRequest.setEnd(LocalDate.parse("2025-06-30"));
        return paymentInfoRequest;
    }

    Map startReportSummary(String startDate, String endDate, boolean payrollSimulation) {
        return webTestClient.post()
                .uri("/compute/payroll/start")
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(createPayload(startDate, endDate, payrollSimulation))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    ReportResponse getReportSummary() {
        return startAndAwaitReport(createPayload(null, null));
    }

    ReportResponse getReportSummaryCustom(String companyId) {
        return startAndAwaitReport(customCreatePayload(companyId));
    }

    protected ReportResponse startAndAwaitReport(PaymentInfoRequest request) {
        Map startResponse = webTestClient.post()
                .uri("/compute/payroll/start")
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        String jobId = String.valueOf(startResponse.get("jobId"));
        JobStatus jobStatus = awaitJobCompletion(jobId);
        assert jobStatus != null && jobStatus.getReportId() != null;
        return getReportById(jobStatus.getReportId());
    }

    protected JobStatus awaitJobCompletion(String jobId) {
        JobStatus jobStatus = null;
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            jobStatus = getStatus(jobId);
            if (jobStatus != null && ("COMPLETED".equalsIgnoreCase(jobStatus.getStatus())
                    || "FAILED".equalsIgnoreCase(jobStatus.getStatus()))) {
                break;
            }
        }
        return jobStatus;
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

    protected ReportResponse getReportByReportId(String url, RetrieveSummaryElementRequest request) {
        return webTestClient.post()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<ReportResponse>() {})
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

    protected Map<String, Object> getReportByFilter(String url, EmployeeFilterRequest employeeFilterRequest) {
        return webTestClient.post()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(employeeFilterRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();
    }

    protected Map<String, Object> getVarinceDetails(String url, EmployeeFilterRequest employeeFilterRequest) {
        return webTestClient.post()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(employeeFilterRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
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

    protected JobStatus getStatus(String jobId) {

        String url = UriComponentsBuilder.fromHttpUrl("http://localhost:" + port + "/compute/payroll/status/" + jobId)
                    .toUriString();

        return webTestClient.get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<JobStatus>() {})
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

    ReportResponse getReportById(String reportId) {
        // @GetMapping("/{companyId}/")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/by-reportId";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX)
                .toUriString();
        RetrieveSummaryElementRequest retrieveSummaryElementRequest = new RetrieveSummaryElementRequest();
        retrieveSummaryElementRequest.setReportId(reportId);
        return getReportByReportId(url, retrieveSummaryElementRequest);
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

    Map<String, Object> geReportByFilter(String summaryId) {
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/filterReports";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX).toUriString();
        EmployeeFilterRequest employeeFilterRequest = new EmployeeFilterRequest();
        employeeFilterRequest.setCompanyID("1234567");
        employeeFilterRequest.setReportId(summaryId);
        employeeFilterRequest.setPage(0);
        employeeFilterRequest.setSize(10);
        return getReportByFilter(url, employeeFilterRequest);
    }

    Map<String, Object> getVarianceDetails(String reportId, String header) {
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/variance-details";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX).toUriString();
        EmployeeFilterRequest employeeFilterRequest = new EmployeeFilterRequest();
        employeeFilterRequest.setCompanyID("1234567");
        employeeFilterRequest.setHeader(header);
        employeeFilterRequest.setReportId(reportId);
        employeeFilterRequest.setPage(0);
        employeeFilterRequest.setSize(10);
        return getVarinceDetails(url, employeeFilterRequest);
    }

    ReportResponse getReportByStartDateAndCompanyId() {
        // @GetMapping("/get-by-start-date/{companyId}/{startDate}")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "get-by-start-date/" + TEST_COMPANY_ID + "/" + LocalDate.now())
                .toUriString();
        return getReportDirect(url);
    }

    void approvePayroll() {
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        UpdatePayrollStatusRequest request = UpdatePayrollStatusRequest.builder()
                .companyId(TEST_COMPANY_ID)
                .status(PayrollStatus.APPROVED)
                .build();
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "update-report-status").toUriString();
        updatePayrollStatus(url, request);
    }

    void approvePayroll(UpdateReportRequest updateReportRequest) {
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        UpdatePayrollStatusRequest request = UpdatePayrollStatusRequest.builder()
                .reportId(updateReportRequest.getReportId() != null
                        ? java.util.UUID.fromString(updateReportRequest.getReportId()) : null)
                .companyId(updateReportRequest.getCompanyId())
                .status(updateReportRequest.getPayrollStatus() != null
                        ? updateReportRequest.getPayrollStatus() : PayrollStatus.APPROVED)
                .build();
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "update-report-status").toUriString();
        updatePayrollStatus(url, request);
    }

    void updatePayrollStatus(String url, UpdatePayrollStatusRequest request) {
        webTestClient.put()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    void cancelPayroll() {
        // cancel endpoint is currently disabled; no-op for compatibility with existing tests
    }


    ReportResponse getPaymentDetailsByEmployeeAndCompanyId() {
        //  @GetMapping("/paymentDetails/get-by-employee")
        String URL_PREFIX = "http://localhost:" + port + "/compute/reports/";
        String url = UriComponentsBuilder.fromHttpUrl(URL_PREFIX + "paymentDetails/get-by-employee")
                .queryParam("employeeId", TEST_EMPLOYEE_ID)
                .queryParam("startDate", "2025-06-01")
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

    public PaymentSettingsResponse getOffCyclePaymentDetails (PaymentInfo paymentInfo) {

        var paymentSettings = paymentInfo.getPaymentSettings();
        return paymentSettings
                .stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.GROSS_EARNING))
                .findFirst().orElseGet(PaymentSettingsResponse::new);
    }

}