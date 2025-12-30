package com.xykine.computation.service;

import com.xykine.computation.entity.*;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.repo.*;
import com.xykine.computation.request.*;
import com.xykine.computation.response.*;

import com.xykine.computation.session.SessionCalculationObject;
import com.xykine.computation.utils.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xykine.payroll.model.*;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.xykine.computation.exceptions.PayrollReportNotException;
import com.xykine.computation.exceptions.PayrollUnmodifiableException;

@Service
@RequiredArgsConstructor
public class ReportPersistenceServiceImpl implements ReportPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    private final AuditTrailService auditTrailService;
    private final PayrollReportSummaryRepo payrollReportSummaryRepo;
    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final DashboardDataService dashboardDataService;
    private final YTDReportRepo ytdReportRepo;
    private final PayrollAsyncService payrollAsyncService;
    private final JobStatusStore jobStatusStore;
    private final ComputationConstantsRepo computationConstantsRepo;
    private final EmployeeMetadataService employeeMetadataService;
    private final AdminService adminService;
    private final ComputeService computeService;
    private final PayrollVarianceDetailsRepo payrollVarianceDetailsRepo;
    private final PayrollVarianceDetailsCustomizedRepo payrollVarianceDetailsCustomizedRepo;
    private final WorkflowService workflowService;
    private final PayrollReportDetailStatusService payrollReportDetailStatusService;
    private final PayrollReportSummaryCustomFilter payrollReportSummaryCustomFilter;

    @Autowired
    private SessionCalculationObject sessionCalculationObject;

    @Override
    @Async
    public void computePayrollAsync(Consumer<JobStatusStore> progressCallback,
                                    String jobId, String authorizationHeader,
                                    PaymentInfoRequest paymentRequest) {
        long startTime = System.currentTimeMillis();

        jobStatusStore.updateJob(jobId, "IN_PROGRESS", "Computation started", "");
        progressCallback.accept(jobStatusStore);
        int totalNumberOfPay;
        try {

            List<PaymentInfo> paymentInfoList = adminService.getPaymentInfoList(paymentRequest, authorizationHeader);
            LOGGER.info("PaymentInfoList size: {}", paymentInfoList.size());
//            paymentInfoList.stream().filter(e -> e.getEmployeeID().equalsIgnoreCase("691e9b1dbab63576430b5e98")).forEach(e -> LOGGER.info("payment info {}", e));

            PayrollReportSummary simulatedSummary = payrollReportSummaryRepo
                    .findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(String.valueOf(paymentRequest.getStart()), paymentRequest.getCompanyId(), true);
            if (simulatedSummary != null && !paymentRequest.isPayrollSimulation()) {
                jobStatusStore.updateJob(jobId, "COMPLETED", "Payroll computation complete", String.valueOf(simulatedSummary.getId()));
                progressCallback.accept(jobStatusStore);
                PayrollReportSummary payrollReportSummary = payrollReportSummaryRepo.findPayrollReportSummaryById(UUID.fromString(String.valueOf(simulatedSummary.getId())));
                payrollReportSummary.setPayrollSimulation(false);
                payrollReportSummary.setPayrollStatus(PayrollStatus.PENDING);
                payrollReportSummaryRepo.save(payrollReportSummary);

                payrollAsyncService.updateDetailStatusToPendingAsync(String.valueOf(simulatedSummary.getId()));

                StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
                startWorkflowRequest.setEntity("PAYROLL");
                startWorkflowRequest.setPayrollType("PAYROLL");
                startWorkflowRequest.setPayPeriod(formatToMonthYear(payrollReportSummary.getStartDate()));
                startWorkflowRequest.setPayrollId(payrollReportSummary.getId().toString());
                startWorkflowRequest.setUserId(AuthUtility.getCurrentUser());
                startWorkflowRequest.setCompanyId(paymentRequest.getCompanyId());
                startWorkflowRequest.setPayrollType(payrollReportSummary.isOffCycle() ? "OffCycle" : "Regular");
                startWorkflowRequest.setNumberOfPays(paymentInfoList.size());
                startWorkflowRequest.setNumberOfEmployees(payrollReportSummary.getTotalNumberOfEmployees());
                //This is intentional to display the total gross on the payroll card instead of the net pay, so we are setting TOTAL_GROSS_PAY
                startWorkflowRequest.setNetPay(ReportUtils.transform(payrollReportSummary).getSummary().getSummary().get(MapKeys.TOTAL_GROSS_PAY));
                startWorkflowRequest.setCreatedBy(payrollReportSummary.getCreatedBy());

                workflowService.startWorkflow(startWorkflowRequest, authorizationHeader);

                return;
            }

            if (simulatedSummary != null && paymentRequest.isPayrollSimulation()) {
                payrollReportSummaryRepo.deleteById(simulatedSummary.getId());
                payrollReportDetailRepo.deleteAllBySummaryId(simulatedSummary.getId().toString());
            }

            EmployeeFilterRequest employeeFilterRequest = new EmployeeFilterRequest();
            employeeFilterRequest.setCompanyID(paymentRequest.getCompanyId());
            Map<String, List<String>> costCenters = adminService.getCostCenterDetails(employeeFilterRequest,authorizationHeader);
            sessionCalculationObject.setCostCenters(costCenters);

            sessionCalculationObject = OperationUtils.doPreflight(
                    sessionCalculationObject,
                    computationConstantsRepo,
                    employeeMetadataService,
                    paymentRequest
            );

            if (paymentInfoList == null || paymentInfoList.isEmpty()) {
                throw new PayrollValidationException("No payment information found for request");
            }

            long startTimeC = System.currentTimeMillis();

            PaymentComputeResponse computeResponse = computeService.computePayroll(paymentInfoList);
            long endTimeC = System.currentTimeMillis();

            LOGGER.info("Total computePayroll processing time--------> {} ms", endTimeC - startTimeC);

            computeResponse = OperationUtils.refineResponse(computeResponse, sessionCalculationObject, paymentRequest);
            ReportResponse reportResponse = serializeAndSaveReport(computeResponse, paymentRequest.getCompanyId());
            jobStatusStore.updateJob(jobId, "COMPLETED", "Payroll computation complete", reportResponse.getReportId());
            progressCallback.accept(jobStatusStore);

            long endTime = System.currentTimeMillis();
            LOGGER.info("Total computation processing time {} ms", endTime - startTime);

        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Exception occurred while computing payroll for companyId: {}", paymentRequest.getCompanyId(), e);
            jobStatusStore.updateJob(jobId, "FAILED", e.getMessage(), "");
            progressCallback.accept(jobStatusStore);
        }
    }

    private static String formatToMonthYear(String inputDate) {
        // Parse the date string (expects format "yyyy-MM-dd")
        LocalDate date = LocalDate.parse(inputDate);

        // Format to "MMM, yyyy" (e.g., "Feb, 2026")
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM, yyyy", Locale.ENGLISH);

        return date.format(formatter);
    }


    //TODO review and rework to handle properly
    @Override
    @Async
    public void computeOffCyclePayrollAsync(Consumer<JobStatusStore> progressCallback,
                                    String jobId, String authorizationHeader,
                                    PaymentInfoRequest paymentRequest) {
        jobStatusStore.updateJob(jobId, "IN_PROGRESS", "Computation started", "");
        progressCallback.accept(jobStatusStore);
        try {
            sessionCalculationObject = OperationUtils.doPreflight(
                    sessionCalculationObject,
                    computationConstantsRepo,
                    employeeMetadataService,
                    paymentRequest
            );

            List<PaymentInfo> paymentInfoList = adminService.getPaymentInfoList(paymentRequest, authorizationHeader);
            if (paymentInfoList == null || paymentInfoList.isEmpty()) {
                throw new PayrollValidationException("No payment information found for request");
            }

            LOGGER.info("PaymentInfo List size: {}", paymentInfoList.size());
            PaymentComputeResponse computeResponse = computeService.computePayroll(paymentInfoList);
            computeResponse = OperationUtils.refineResponse(computeResponse, sessionCalculationObject, paymentRequest);
            ReportResponse reportResponse = serializeAndSaveReport(computeResponse, paymentRequest.getCompanyId());

            jobStatusStore.updateJob(jobId, "COMPLETED", "Payroll computation complete", reportResponse.getReportId());
            progressCallback.accept(jobStatusStore);


            PayrollReportSummary payrollReportSummary = payrollReportSummaryRepo.findPayrollReportSummaryById(UUID.fromString(String.valueOf(reportResponse.getReportId())));

            StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
            startWorkflowRequest.setEntity("PAYROLL");
            startWorkflowRequest.setPayrollType("PAYROLL");
            startWorkflowRequest.setPayrollId(payrollReportSummary.getId().toString());
            startWorkflowRequest.setUserId(AuthUtility.getCurrentUser());
            startWorkflowRequest.setCompanyId(paymentRequest.getCompanyId());
            startWorkflowRequest.setPayrollType(payrollReportSummary.isOffCycle() ? "OffCycle" : "Regular");
            startWorkflowRequest.setNumberOfPays(payrollReportDetailRepo.countBySummaryId(payrollReportSummary.getId().toString()));
            startWorkflowRequest.setNumberOfEmployees(payrollReportSummary.getTotalNumberOfEmployees());
            startWorkflowRequest.setNetPay(ReportUtils.transform(payrollReportSummary).getSummary().getSummary().get(MapKeys.TOTAL_GROSS_PAY));
            workflowService.startWorkflow(startWorkflowRequest, authorizationHeader);

        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Exception occurred while computing payroll for companyId: {}", paymentRequest.getCompanyId(), e);
            jobStatusStore.updateJob(jobId, "FAILED", e.getMessage(), "");
            progressCallback.accept(jobStatusStore);
        }
    }

    public Map<String, Map<String, BigDecimal>> getSummaryVarianceDetails(String reportId, List<String> employeeIds){
        PayrollVarianceDetailsCustomized payrollVarianceDetails = payrollVarianceDetailsCustomizedRepo.findById(UUID.fromString(reportId)).orElse(null);
        Map<String, Map<String, BigDecimal>> summaryVarianceDetails = new HashMap<>();
        if (payrollVarianceDetails == null) {
            return summaryVarianceDetails;
        }
        PayCompteVarianceDetailsCustomized payComputeVarianceDetails = ReportUtils.transform(payrollVarianceDetails).getPayComputeVarianceDetails();
        summaryVarianceDetails = payComputeVarianceDetails.getSummaryDetailsVariance();

        return summaryVarianceDetails.entrySet()
                .stream()
                .filter(variance -> employeeIds.contains(variance.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    }

    @Override
    public PaginatedReportSummaryResponse getReportSummaryByFilter(ReportFilterRequest request) {
        return payrollReportSummaryCustomFilter.filterReports(request);
    }

    public ConcurrentHashMap<String, Set<SummaryDetail>> getSummaryVarianceDetails(String reportId, List<String> employeeIds, String header) {
        PayrollVarianceDetails payrollVarianceDetails = payrollVarianceDetailsRepo.findById(UUID.fromString(reportId)).orElse(null);
        ConcurrentHashMap<String, Set<SummaryDetail>> summaryVarianceDetails = new ConcurrentHashMap<>();
        if (payrollVarianceDetails == null) {
            return summaryVarianceDetails;
        }

        PayComputeVarianceDetails payComputeVarianceDetails = ReportUtils.transform(payrollVarianceDetails).getPayComputeVarianceDetails();
        summaryVarianceDetails = payComputeVarianceDetails.getSummaryDetailsVariance();

        boolean isFilteredBHeader = header != null && !header.isEmpty();
        List<String> headers = new ArrayList<>();

        if (isFilteredBHeader) {
            headers.add(header);
        } else {
            headers.addAll(summaryVarianceDetails.keySet());
        }

        return summaryVarianceDetails.entrySet()
                .stream()
                .filter(entry -> headers.contains(entry.getKey()))
                .map(entry -> {
                    // Filter the Set<SummaryDetail> for this key
                    Set<SummaryDetail> filteredSet = entry.getValue()
                            .stream()
                            .filter(sd -> employeeIds.contains(sd.getEmployeeId()))
                            .collect(Collectors.toSet());
                    return Map.entry(entry.getKey(), filteredSet);
                })
                // Keep only entries where the filtered set is not empty
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toConcurrentMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        ConcurrentHashMap::new
                ));
    }

    @Transactional
    public ReportResponse serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse, String companyId)
            throws IOException {
        long startTime = System.currentTimeMillis();
        ReportResponse reportResponse;
        try {
            deleteReportByDate(
//                    paymentComputeResponse.getStart(),
//                    companyId,
//                    paymentComputeResponse.isOffCycle(),
//                    false,
//                    paymentComputeResponse.getOffCycleId(),
//                    //TODO validate this to make sure is the reportId
//                    String.valueOf(paymentComputeResponse.getId())
                    UpdateReportRequest.builder()
                            .reportId(String.valueOf(paymentComputeResponse.getId()))
                            .companyId(companyId)
                            .offCycle(paymentComputeResponse.isOffCycle())
                            .cancelPayroll(false)
                            .offCycleId(paymentComputeResponse.getOffCycleId())
                            .build());
            reportResponse = getReportResponse(paymentComputeResponse, companyId);
            //  }
        } catch (RuntimeException e) {
            LOGGER.info(" exception {} ", e.toString());
            throw e;
        }
        long endTime = System.currentTimeMillis();
        LOGGER.info(" Process time {} ms", endTime - startTime);
        logGenerateReportEvent(companyId, reportResponse);
        return reportResponse;
    }

    private ReportResponse getReportResponse(PaymentComputeResponse paymentComputeResponse, String companyId) {
        PayrollReportSummary previousReportSummary = payrollReportSummaryRepo.findTopByCompanyIdAndPayrollStatusAndOffCycleFalseOrderByEndDateDesc(companyId, PayrollStatus.COMPLETED).orElse(null);
        PaymentInfo paymentInfo = paymentComputeResponse.getReport().get(0);
        long totalNumberOfEmployees = paymentInfo.getTotalNumberOfEmployees();
        PaymentFrequencyEnum paymentFrequency = paymentInfo.getSalaryFrequency();

        String previousReportSummaryId = previousReportSummary != null ? String.valueOf(previousReportSummary.getId()) : null;

        PayComputeSummaryResponse payComputeSummaryResponse = PayComputeSummaryResponse.builder()
                .summary(paymentComputeResponse.getSummary())
                .summaryDetails(paymentComputeResponse.getSummaryDetails())
                .summaryVariance(processSummaryVariance(paymentComputeResponse.getSummary(), previousReportSummary))
                .costCenterSummary(paymentComputeResponse.getCostCenterSummary())
                //.summaryDetailsVariance(processSummaryDetailsVariance(paymentComputeResponse.getSummaryDetails(), previousReportSummary))
                .build();

        PayComputeVarianceDetails payComputeVarianceDetails = PayComputeVarianceDetails.builder()
                .summaryDetailsVariance(processSummaryDetailsVariance(paymentComputeResponse.getSummaryDetails(), previousReportSummary))
                .build();

        PayrollVarianceDetails payrollVarianceDetails = PayrollVarianceDetails.builder()
                .id(paymentComputeResponse.getId())
                .summaryVarianceDetails(ReportUtils.serializeResponse(payComputeVarianceDetails))
                .build();

        PayrollReportSummary payrollReportSummary = PayrollReportSummary.builder()
                .id(paymentComputeResponse.getId())
                .companyId(companyId)
                .offCycleId(paymentComputeResponse.getOffCycleId())
                .startDate(paymentComputeResponse.getStart())
                .endDate(paymentComputeResponse.getEnd())
                .report(ReportUtils.serializeResponse(payComputeSummaryResponse))
                .createdDate(LocalDateTime.now())
                .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                .offCycle(paymentComputeResponse.isOffCycle())
                .totalNumberOfEmployees(totalNumberOfEmployees)
                .payrollStatus(paymentComputeResponse.isPayrollSimulation() ? PayrollStatus.SIMULATED : PayrollStatus.INITIATED)
                .paymentFrequency(paymentFrequency)
                .code(generateReportCode(paymentComputeResponse.getStart(), paymentComputeResponse.isOffCycle(), totalNumberOfEmployees))
                .build();

        payrollVarianceDetailsRepo.save(payrollVarianceDetails);
        payrollReportSummaryRepo.save(payrollReportSummary);
        payrollAsyncService.saveReportDetails(paymentComputeResponse, companyId, previousReportSummaryId);

        return getPayRollReport(paymentComputeResponse.getId());
    }

    private static ConcurrentHashMap<String, Set<SummaryDetail>> processSummaryDetailsVariance(
            ConcurrentHashMap<String, Set<SummaryDetail>> currentSummaryDetails,
            PayrollReportSummary previousPayrollReportSummary) {

        ConcurrentHashMap<String, Set<SummaryDetail>> summaryDetailsVariance = new ConcurrentHashMap<>();

        if (previousPayrollReportSummary == null) {
            return summaryDetailsVariance;
        } else {
            Map<String, Set<SummaryDetail>> previousSummaryDetails =
                    ReportUtils.transform(previousPayrollReportSummary).getSummary().getSummaryDetails();

            // Union of all keys
            Set<String> allKeys = ConcurrentHashMap.newKeySet();
            allKeys.addAll(currentSummaryDetails.keySet());
            allKeys.addAll(previousSummaryDetails.keySet());

            for (String key : allKeys) {
                Set<SummaryDetail> currentDetails = currentSummaryDetails.getOrDefault(key, Collections.emptySet());
                Set<SummaryDetail> previousDetails = previousSummaryDetails.getOrDefault(key, Collections.emptySet());

                Map<String, BigDecimal> currentSum = sumByEmployee(currentDetails);
                Map<String, BigDecimal> previousSum = sumByEmployee(previousDetails);

                // Use representative objects for employeeName/department
                Map<String, SummaryDetail> representativeMap = new ConcurrentHashMap<>();
                currentDetails.forEach(d -> representativeMap.putIfAbsent(d.getEmployeeId(), d));
                previousDetails.forEach(d -> representativeMap.putIfAbsent(d.getEmployeeId(), d));

                Set<SummaryDetail> varianceDetails = Collections.newSetFromMap(new ConcurrentHashMap<>());

                // Calculate variance for employees present in current
                currentSum.forEach((empId, currTotal) -> {
                    BigDecimal prevTotal = previousSum.getOrDefault(empId, BigDecimal.ZERO);
                    BigDecimal diff = currTotal.subtract(prevTotal);
                    if (diff.compareTo(BigDecimal.ZERO) != 0) {
                        SummaryDetail rep = representativeMap.get(empId);
                        varianceDetails.add(new SummaryDetail(
                                rep.getEmployeeId(),
                                rep.getEmployeeName(),
                                rep.getDepartmentName(),
                                currTotal,
                                diff
                        ));
                    }
                });

                // Include negative variance for employees only in previous
                previousSum.forEach((empId, prevTotal) -> {
                    if (!currentSum.containsKey(empId)) {
                        SummaryDetail rep = representativeMap.get(empId);
                        varianceDetails.add(new SummaryDetail(
                                rep.getEmployeeId(),
                                rep.getEmployeeName(),
                                rep.getDepartmentName(),
                                prevTotal,
                                prevTotal.negate()
                        ));
                    }
                });

                if (!varianceDetails.isEmpty()) {
                    summaryDetailsVariance.put(key, varianceDetails);
                }
            }
        }

        return summaryDetailsVariance;
    }

    private static Map<String, BigDecimal> sumByEmployee(Collection<SummaryDetail> details) {
        Map<String, BigDecimal> map = new ConcurrentHashMap<>();
        details.forEach(d -> map.merge(
                d.getEmployeeId(),
                d.getValue() == null ? BigDecimal.ZERO : d.getValue(),
                BigDecimal::add
        ));
        return map;
    }

    private Map<String, BigDecimal> processSummaryVariance(Map<String, BigDecimal> currentSummary, PayrollReportSummary previousPayrollReportSummary) {
        Map<String, BigDecimal> summaryVariance = new HashMap<>();

        if (previousPayrollReportSummary == null) {
            // If previousPayrollReportSummary is null, set all values to zero
            for (Map.Entry<String, BigDecimal> entry : currentSummary.entrySet()) {
                summaryVariance.put(entry.getKey(), BigDecimal.ZERO);
            }
        } else {
            // If previousPayrollReportSummary is not null, calculate the differences
            var previousSummary = ReportUtils.transform(previousPayrollReportSummary).getSummary().getSummary();

            for (Map.Entry<String, BigDecimal> entry : currentSummary.entrySet()) {
                String key = entry.getKey();
                BigDecimal currentValue = entry.getValue();
                BigDecimal previousValue = previousSummary.get(key);

                // If previousValue is not present, assume it to be BigDecimal.ZERO
                if (previousValue == null) {
                    previousValue = BigDecimal.ZERO;
                }
                // Calculate the difference
                BigDecimal difference = currentValue.subtract(previousValue);

                // Add the difference to the differences map
                summaryVariance.put(key, difference);
            }
        }
        return summaryVariance;
    }

    public ReportResponse getPayRollReport(UUID id) {
        PayrollReportSummary summary = payrollReportSummaryRepo.findPayrollReportSummaryById(id);
        if (summary != null) return ReportUtils.transform(summary);
        throw new RuntimeException("Report with id: " + id + " was not found");
    }

    @Override
    public ReportResponse getPayRollReport(String starDate, String companyId) {
        PayrollReportSummary payrollReportSummary = payrollReportSummaryRepo
                .findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(starDate, companyId, false);
        if (payrollReportSummary == null) {
            return null;
        }
        //auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Get payroll report with start date :" + starDate + " for company id : " + companyId);
        return ReportUtils.transform(payrollReportSummary);
    }

    @Override
    public Map<String, Object> getPayRollReportByType(ReportByTypeRequest request, int page, int size) {
        Pageable paging = PageRequest.of(page, size);

        // if (category == null) {
        Page<PayrollReportSummary> payrollReportReportPage = payrollReportSummaryRepo
                .findAllByCompanyIdAndStartDateBetween(
                        request.getCompanyId(),
                        getStartDateRange(request.getStart(), request.getEnd()),
                        getEndDateRange(request.getEnd()), paging);

        List<PayrollReportSummary> payrollReportReportPageList = payrollReportReportPage.getContent();

        if (request.getCategory() != null) {
            boolean categoryFound = request.getCategory().compareTo(PayrollCategory.OFFCYLE) == 0 ? true : false;
            payrollReportReportPageList = payrollReportReportPageList.stream().filter(x -> x.isOffCycle() == categoryFound).toList();
        }

        if (request.getPayrollStatus() != null) {
            payrollReportReportPageList = payrollReportReportPageList.stream().filter(x -> x.getPayrollStatus().compareTo(request.getPayrollStatus()) == 0).toList();
        }

        payrollReportReportPage = new PageImpl<>(
                payrollReportReportPageList,
                paging,
                payrollReportReportPageList.size()   // total elements = filtered size
        );

        return retrievePayroll(payrollReportReportPage);
    }

    @Override
    public Map<String, Object> getPayRollReportDetailByType(ReportByTypeRequest request, int page, int size) {
        var isOffCycle = request.getCategory().equals(PayrollCategory.OFFCYLE);
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo
                .findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateBetweenAndOffCycle(
                        request.getCompanyId(),
                        request.getEmployeeID(),
                        getStartDateRange(request.getStart(), request.getEnd()),
                        getEndDateRange(request.getEnd()),
                        isOffCycle, paging);

        return retrievePayrollDetails(payrollReportDetailPage, null);
    }


    private List<ReportResponse> getPayRollReportOffCycle(String companyId) {
        return payrollReportSummaryRepo
                .findAllByCompanyIdAndPayrollSimulationAndOffCycle(companyId, false, true)
                .stream()
                .map(ReportUtils::transform)
                .collect(Collectors.toList());
    }

    //Pull both all report summary for display on dashboard
    public List<ReportResponse> getPayRollReports(String companyId) {
        return payrollReportSummaryRepo.findAllByCompanyIdOrderByCreatedDateAsc(companyId).stream()
                .map(ReportUtils::transform).toList();
    }

    @Override
    public List<ReportResponse> getPayRollReportsByStatus(String companyId, String status) {
        List<ReportResponse> reports = new ArrayList<>();
        //'COMPLETED. |. PENDING. |. APPROVED. |. SIMULATED'
        if (status != null && status.equalsIgnoreCase("COMPLETED")) {
            List<ReportResponse> firstReport = payrollReportSummaryRepo.findAllByPayrollStatusAndCompanyIdOrderByCreatedDateAsc(PayrollStatus.COMPLETED, companyId).stream()
                    .map(ReportUtils::transform).toList();
            if (!firstReport.isEmpty()) {
                reports.add(firstReport.get(0));
            }
        }
        if (status != null && status.equalsIgnoreCase("APPROVED")) {
            reports = payrollReportSummaryRepo.findAllByPayrollStatusAndCompanyIdOrderByCreatedDateAsc(PayrollStatus.APPROVED, companyId).stream()
                    .map(ReportUtils::transform).toList();
        }
        if (status != null && status.equalsIgnoreCase("PENDING")) {
            reports = payrollReportSummaryRepo.findAllByPayrollStatusAndCompanyIdOrderByCreatedDateAsc(PayrollStatus.PENDING, companyId).stream()
                    .map(ReportUtils::transform).toList();
        }
        //auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Pulled payroll report for company id :" + companyId);
        return reports;
    }

    @Override
    public Map<String, Object> getReportByEmployeeID(String companyId, String employeeID, int page, int size) {
        List<PayrollReportDetail> payrollDetails;
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndEmployeeId(companyId, employeeID, paging);

        Map<String, Object> response = retrievePayrollDetails(payrollReportDetailPage, null);
        auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Pulled payroll report for company id :" + companyId + "and employee id: " + employeeID, companyId);
        return response;
    }

//    @Override
//    public Map<String, Object> getReportByEmployeeIDList(String companyId, List<String> employeeIDList, String summaryId, int page, int size) {
//        Pageable paging = PageRequest.of(page, size);
//        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndEmployeeIdInAndSummaryId(companyId, employeeIDList, summaryId, paging);
//        Map<String, Object> response = retrievePayrolDetails(payrollReportDetailPage);
//        return response;
//    }

    @Override
    public Map<String, Object> getReportByEmployeeIDList(String companyId, List<String> employeeIDList, String summaryId, PaginatedSelectedEmployeeField selectedEmployeeField, int page, int size) {

        Pageable paging = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "fullName"));

        Page<PayrollReportDetail> payrollReportDetailPage ;

        if(employeeIDList.isEmpty()) {
            payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndSummaryId(companyId, summaryId, paging);
        } else {
            //Admin svc already fetched the paginated employeeIDs, that is why we only fetch that specific list with the employeeIds
            Pageable page0 = PageRequest.of(0, size);
            payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndEmployeeIdInAndSummaryId(companyId, employeeIDList, summaryId, page0);
        }

        Map<String, Object> response = retrievePayrollDetails(payrollReportDetailPage, selectedEmployeeField);

        return response;
    }

    private Map<String, Object> retrievePayrollDetails(Page<PayrollReportDetail> payrollReportDetailPage, PaginatedSelectedEmployeeField paginatedSelectedEmployeeField) {
        List<PayrollReportDetail> payrollDetails;
        payrollDetails = payrollReportDetailPage.getContent();
        List<ReportResponse> reportResponses = ReportUtils.transform(payrollDetails);

        if(paginatedSelectedEmployeeField != null && !paginatedSelectedEmployeeField.getSelectedEmployeeFields().isEmpty()) {
            mergeEmployeeFields(reportResponses, paginatedSelectedEmployeeField.getSelectedEmployeeFields());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("payrollDetails", reportResponses);
        assert paginatedSelectedEmployeeField != null;
        response.put("currentPage", paginatedSelectedEmployeeField.getCurrentPage());
        response.put("totalItems", paginatedSelectedEmployeeField.getTotalItems());
        response.put("totalPages", paginatedSelectedEmployeeField.getTotalPages());
        return response;
    }

    private void mergeEmployeeFields(
            List<ReportResponse> reportResponses,
            List<SelectedEmployeeField> selectedEmployeeFields) {

        Map<String, SelectedEmployeeField> fieldMap =
                selectedEmployeeFields.stream()
                        .collect(Collectors.toMap(
                                SelectedEmployeeField::getEmployeeID,
                                Function.identity(),
                                (x, y) -> x
                        ));

        reportResponses.forEach(report -> {
            SelectedEmployeeField match = fieldMap.get(report.getEmployeeId());

            if (match != null) {
                report.setEmployeeCode(match.getEmployeeCode());
                report.setEmployeeHireDate(match.getStartDate());  //
            }
        });
    }


    private Map<String, Object> retrievePayroll(Page<PayrollReportSummary> payrollReportSummaryPage) {
        List<PayrollReportSummary> payrollReportSummaryList;
        payrollReportSummaryList = payrollReportSummaryPage.getContent();
        List<ReportResponse> reportResponses = ReportUtils.transformSummary(payrollReportSummaryList);
//        getReportAnalytics(reportResponses, payrollReportSummaryList.get(0).getCompanyId());
        Map<String, Object> response = new HashMap<>();
        response.put("payrollReportSummary", reportResponses);
        response.put("currentPage", payrollReportSummaryPage.getNumber());
        response.put("totalItems", payrollReportSummaryPage.getTotalElements());
        response.put("totalPages", payrollReportSummaryPage.getTotalPages());
        return response;
    }

//    @Transactional
//    public void updateReportStatus(UpdatePayrollStatusRequest request) {
//        if (request.getStatus().equals(PayrollStatus.REJECTED)) {
//            request.setStatus(PayrollStatus.SIMULATED);
//        }
//
//        PayrollReportSummary existingSummaryReport = payrollReportSummaryRepo.findPayrollReportSummaryByIdAndCompanyId(request.getReportId(), request.getCompanyId()).orElseThrow();
//        existingSummaryReport.setPayrollStatus(request.getStatus());
//        PayrollReportSummary reportResponse = payrollReportSummaryRepo.save(existingSummaryReport);
//
//        //TODO make async
////        updateDetailReportsAndDashboard(request, existingSummaryReport);
//    }



    @Transactional
    public void updateReportStatus(UpdatePayrollStatusRequest request) {

        PayrollReportSummary existingSummaryReport = payrollReportSummaryRepo.findPayrollReportSummaryByIdAndCompanyId(request.getReportId(), request.getCompanyId()).orElseThrow();
        PayrollStatus currentStatus = existingSummaryReport.getPayrollStatus();

        System.out.println("currentStatus---> from database  " + currentStatus);
        System.out.println("requested status---> " + request.getStatus());


        //if we have not approved the payroll, we can go back to simulate which will show as draft from the UI
        // we don't have to roll back the data from the dashboard
        if ((request.getStatus().equals(PayrollStatus.ROLLED_BACK) || request.getStatus().equals(PayrollStatus.REJECTED))
                && payrollNotYetApproved(currentStatus)) {
            request.setStatus(PayrollStatus.SIMULATED);
        }

        existingSummaryReport.setPayrollStatus(request.getStatus());

        payrollReportSummaryRepo.save(existingSummaryReport);

        //Also update the details status, beacuse we use the status to pull the report, since we now keep multiple details,
        //we only want to allow download of only the approved or completed detail reports
        payrollReportDetailStatusService.updateByCompanyAndReport(request);

        if (request.getStatus().equals(PayrollStatus.APPROVED)) {
            if (existingSummaryReport.isOffCycle()) {
                updateDashboardData(AppConstants.payrollCountOffCycle, existingSummaryReport, false);
            } else {
                updateDashboardData(AppConstants.payrollCountRegular, existingSummaryReport, false);
            }
        }

        if ((request.getStatus().equals(PayrollStatus.ROLLED_BACK) || request.getStatus().equals(PayrollStatus.REJECTED))
                && (currentStatus == PayrollStatus.APPROVED)) {
            if (existingSummaryReport.isOffCycle()) {
                updateDashboardData(AppConstants.payrollCountOffCycle, existingSummaryReport, true);
            } else {
                updateDashboardData(AppConstants.payrollCountRegular, existingSummaryReport, true);
            }
        }
    }

    private static boolean payrollNotYetApproved(PayrollStatus currentStatus) {
        System.out.println("currentStatus " + currentStatus);
        return currentStatus != PayrollStatus.APPROVED && currentStatus != PayrollStatus.APPROVED_AUDIT;
    }


//    @Async
//    protected void updateDetailReportsAndDashboard(UpdatePayrollStatusRequest request, PayrollReportSummary existingSummaryReport) {
//        if (request.getStatus().equals(PayrollStatus.APPROVED)) {
//            if (existingSummaryReport.isOffCycle()) {
//                updateDashboardData(AppConstants.payrollCountOffCycle, existingSummaryReport);
//            } else {
//                updateDashboardData(AppConstants.payrollCountRegular, existingSummaryReport);
//            }
////            payrollAsyncService.updateEmployeeLoanAsync(existingSummaryReport.getId().toString(), reportResponse.getCompanyId());
//            payrollAsyncService.updateDetailStatusAsync(existingSummaryReport.getId().toString());
//        }
//    }

//    @Override
//    public boolean deleteReport(UpdateReportRequest request, String token) {
//        auditTrailService.logEvent(AuditTrailEvents.DELETE_REPORT, "Deleted report with start date : " + request.getStartDate() + " company id : " + request.getCompanyId(), request.getCompanyId());
//        var res = deleteReportByDate(request);
//        // Delete the corresponding workflow
//        workflowService.deleteWorkflowByReportId(DeleteStageInstanceRequest.builder()
//                .companyId(request.getCompanyId())
//                .reportId(request.getReportId())
//                .build(), token);
//        return res;
//    }

//    @Override
//    public CompletePayrollResponse completeReport(CompletePayrollRequest request) {
//        PayrollReportSummary existingSummaryReport =
//                payrollReportSummaryRepo.findPayrollReportSummaryByIdAndCompanyIdAndPayrollSimulation(
//                        request.getReportId(),
//                        request.getCompanyId(),
//                        false
//                );
//
////        if (request.isOffCycle()) {
////            existingSummaryReport = payrollReportSummaryRepo.findPayrollReportSummaryByStartDateAndCompanyIdAndOffCycleIdAndPayrollSimulation(
////                    request.getStartDate(),
////                    request.getCompanyId(),
////                    request.getOffCycleId(),
////                    false);
////        } else {
////            existingSummaryReport = payrollReportSummaryRepo
////                    .findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(request.getStartDate(), request.getCompanyId(), false);
////        }
//
//
//
//        if (existingSummaryReport == null) {
//            throw new RuntimeException("Unable to pull payroll report");
//        }
//
//        existingSummaryReport.setPayrollStatus(PayrollStatus.COMPLETED);
//        var payrollReportSummary = payrollReportSummaryRepo.save(existingSummaryReport);
//        logPostReportToFinanceEvent(request.getCompanyId(), payrollReportSummary);
//
//        var response = ReportUtils.transform(payrollReportSummary);
//
//        return CompletePayrollResponse.builder()
//                .companyId(response.getCompanyId())
//                .reportId(response.getReportId())
//                .completedDate(response.getCreatedDate())
//                .payrollStatus(response.getPayrollStatus())
//                .summary(response.getSummary().getSummary())
//                .startDate(response.getStartDate())
//                .endDate(response.getEndDate())
//                .code(response.getCode())
//                .offCycle(response.isOffCycle())
//                .build();
//
//    }

    //TODO we should remove all other resources that link to the report, when a report is deleted
    private boolean deleteReportByDate(UpdateReportRequest request) {
        if (request.isOffCycle() && !request.isCancelPayroll()) return false;
        //canceling offCycle payroll
//        if (isOffCycle) {
//            payrollReportSummaryRepo.deletePayrollReportSummaryByOffCycleIdAndCompanyId(offCycleId, companyId);
//            payrollReportDetailRepo.deleteAllByOffCycleIdAndCompanyId(offCycleId, companyId);
//            return true;
//        }

        var payroll = payrollReportSummaryRepo
                .findPayrollReportSummaryByIdAndCompanyId(UUID.fromString(request.getReportId()), request.getCompanyId());


        if (payroll.isPresent()) {
            var payrollSummary = payroll.get();
            //TODO we want to allow all status to be deleted and only when we already disbursed
//            if (payrollSummary.getPayrollStatus().compareTo(PayrollStatus.APPROVED) == 0 || payrollSummary.getPayrollStatus().compareTo(PayrollStatus.COMPLETED)  == 0) {
            if (payrollSummary.getPayrollStatus().compareTo(PayrollStatus.DISBURSED) == 0) {
                throw new PayrollUnmodifiableException(request.getStartDate());
            }
        }

        //canceling regular payroll
//        payrollReportSummaryRepo.deletePayrollReportSummaryByStartDateAndCompanyId(startDate, companyId);
//        payrollReportDetailRepo.deleteAllByStartDateAndCompanyId(LocalDate.parse(startDate), companyId);
        payrollReportSummaryRepo.deletePayrollReportSummaryByIdAndCompanyId(UUID.fromString(request.getReportId()), request.getCompanyId());
        payrollReportDetailRepo.deleteAllBySummaryIdAndCompanyId(request.getReportId(), request.getCompanyId());
        return true;
    }


    @Override
    public Map<String, Object> getPaymentDetails(String summaryId, String companyId, String fullName, int page, int size) {
        List<PayrollReportDetail> payrollDetails = new ArrayList<>();
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailBySummaryIdAndCompanyIdAndFullNameContainingIgnoreCase(summaryId, companyId, fullName, paging);

        payrollDetails = payrollReportDetailPage.getContent();
        List<ReportResponse> reportResponses = ReportUtils.transform(payrollDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("payrollDetails", reportResponses);
        response.put("currentPage", payrollReportDetailPage.getNumber());
        response.put("totalItems", payrollReportDetailPage.getTotalElements());
        response.put("totalPages", payrollReportDetailPage.getTotalPages());
        //auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Retrieved report detail for report id :" +  summaryId);
        return response;
    }

    @Override
    public Map<String, Object> getPaymentDetailForDates(String employeeId, String companyId, List<String> endDates, int page, int size) {
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo
                .findPayrollReportDetailByEmployeeIdAndCompanyId(
                        employeeId,
                        companyId,
                        paging);

        List<ReportResponse> reportResponses = ReportUtils.transform(payrollReportDetailPage.getContent()).stream()
                .filter(x -> endDates.contains(x.getEndDate()))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("payrollDetails", reportResponses);
        response.put("currentPage", payrollReportDetailPage.getNumber());
        response.put("totalItems", payrollReportDetailPage.getTotalElements());
        response.put("totalPages", payrollReportDetailPage.getTotalPages());
        //auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Retrieved report detail for employeeId id :" +  employeeId);
        return response;
    }

    @Override
    public ReportResponse getPaymentDetailsByEmployee(String employeeId, String startDate, String companyId) {
        List<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo
                .findPayrollReportDetailByEmployeeIdAndCompanyId(
                        employeeId,
                        companyId);
        List<ReportResponse> reportResponses = ReportUtils.transform(payrollReportDetailPage);

        var res = reportResponses.stream().filter(d -> d.getStartDate().equals(startDate)).findFirst();

        if (res.isEmpty()) {
            throw new PayrollReportNotException(startDate);
        }
        //auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Retrieved payment detail for employee id :" +  employeeId + " companyId : " + companyId + " startDate : " + startDate);
        return res.get();
    }

//    @Override
//    public Map<String, Object> getPayRollReports(String companyId, int page, int size) {
//        int safePage = Math.max(0, page);
//        int safeSize = size > 0 ? size : 10;
//
//        // Fetch all persisted reports for the company (unpaged)
//        Page<PayrollReportSummary> persistedPage =
//                payrollReportSummaryRepo.findAllByCompanyIdOrderByCreatedDateAsc(companyId, Pageable.unpaged());
//
//        List<ReportResponse> persisted = persistedPage.getContent()
//                .stream()
//                .map(ReportUtils::transform)
//                .toList();
//
//        // Fetch all simulated reports
//        List<ReportResponse> simulated = getPayRollReportSimulates(companyId);
//
//        // Merge both, sort by createdDate ASC
//        List<ReportResponse> combined = new ArrayList<>(persisted.size() + simulated.size());
//        combined.addAll(persisted);
//        combined.addAll(simulated);
//
//        combined.sort(Comparator.comparing(
//                rr -> parseCreated(rr.getCreatedDate()),
//                Comparator.nullsLast(Comparator.naturalOrder())
//        ));
//
//        // In-memory pagination
//        int totalItems = combined.size();
//        int fromIndex = Math.min(safePage * safeSize, totalItems);
//        int toIndex = Math.min(fromIndex + safeSize, totalItems);
//        List<ReportResponse> pageContent = combined.subList(fromIndex, toIndex);
//
//        int totalPages = (int) Math.ceil((double) totalItems / safeSize);
//
//        Map<String, Object> response = new HashMap<>();
//        response.put("reports", pageContent);
//        response.put("currentPage", safePage);
//        response.put("totalItems", totalItems);
//        response.put("totalPages", totalPages);
//        response.put("pageSize", safeSize);
//        return response;
//    }

    private static LocalDateTime parseCreated(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e1) {
            try {
                return LocalDate.parse(value).atStartOfDay();
            } catch (Exception e2) {
                return null;
            }
        }
    }


    @Override
    public List<ReportAnalytics> getReportAnalytics(String companyId, int page, int size) {
        //Get items payment items stat
        //get current instance

        Pageable paging = PageRequest.of(page, size);
//        Page<PayrollReportSummary> payrollReportSummaryPage = payrollReportSummaryRepo.findPayrollReportSummaryByCompanyIdAndPayrollSimulationOrderByCreatedDateDesc(companyId, true, paging);
        Page<PayrollReportSummary> payrollReportSummaryPage = payrollReportSummaryRepo.findPayrollReportSummaryByCompanyIdOrderByCreatedDateDesc(companyId, paging);
        var reportAnalytics = payrollReportSummaryPage.getContent()
                .stream()
                .filter(r -> r != null && r.getId() != null)
                .map(x -> new ReportAnalytics(
                        x.getStartDate(),
                        x.getTotalNumberOfEmployees(),
                        payrollReportDetailRepo.countBySummaryId(x.getId().toString()),
                        ReportUtils.transform(x).getSummary().getSummary().get(MapKeys.TOTAL_NET_PAY),
                        getReportStatus(x),
                        x.getId().toString(),
                        x.getCompanyId(),
                        x.isOffCycle(),
                        x.getOffCycleId(),
                        x.isOffCycle() ? "Off-Cycle" : "Regular",
                        x.getCreatedDate().toString()
                ))
                .toList();
        return !reportAnalytics.isEmpty() ? reportAnalytics : new ArrayList<>();
    }

    private String getReportStatus(PayrollReportSummary report) {
        return report.getPayrollStatus().name();
    }


    @Override
    public YTDReport getYTDReport(String employeeId, String companyId) {
        return ytdReportRepo.findYTDReportByEmployeeIdAndCompanyId(employeeId, companyId)
                .orElseGet(() -> {
                    YTDReport newReport = new YTDReport();
                    newReport.setEmployeeId(employeeId);
                    newReport.setCompanyId(companyId);
                    newReport.setBasicSalary(BigDecimal.ZERO);
                    newReport.setGrossPay(BigDecimal.ZERO);
                    newReport.setNetPay(BigDecimal.ZERO);
                    newReport.setNhf(BigDecimal.ZERO);
                    newReport.setPayeeTax(BigDecimal.ZERO);
                    newReport.setPension(BigDecimal.ZERO);
                    newReport.setTaxableIncome(BigDecimal.ZERO);
                    return newReport;
                });
    }

    @Override
    public List<YTDReport> getYTDReports(YtdRequest request) {
        return ytdReportRepo.findYTDReportByEmployeeIdInAndCompanyId(request.getEmployeeIds(), request.getCompanyId());
    }

    private CompletableFuture<Void> saveReportDetailsAsync(
            PaymentComputeResponse paymentComputeResponse, String companyId) {

        List<PaymentInfo> paymentInfoList = Optional.ofNullable(paymentComputeResponse.getReport())
                .orElse(Collections.emptyList());

        return CompletableFuture.runAsync(() -> {
            try {
                if (paymentInfoList.isEmpty()) {
                    LOGGER.warn("No payment info found for companyId={}", companyId);
                    return;
                }

                String summaryId = String.valueOf(paymentComputeResponse.getId());

                // 🔹 Step 1: Group by (employeeId, startDate, endDate, summaryId)
                Map<String, List<PaymentInfo>> grouped = paymentInfoList.stream()
                        .collect(Collectors.groupingBy(
                                x -> buildKey(companyId, x.getEmployeeID(), x.getStartDate(), x.getEndDate(), summaryId)
                        ));

                // 🔹 Step 2: Merge PaymentInfo objects in each group
                List<PayrollReportDetail> mergedReports = grouped.values().stream()
                        .map(group -> {
                            try {
                                PaymentInfo merged = group.stream()
                                        .reduce(this::mergePaymentInfos)
                                        .orElse(null);

                                if (merged == null) return null;

                                PayComputeDetailResponse detailResponse = PayComputeDetailResponse.builder()
                                        .report(merged)
                                        .build();

                                PayrollReportDetail report = PayrollReportDetail.builder()
                                        .id(UUID.randomUUID().toString())
                                        .companyId(companyId)
                                        .employeeId(merged.getEmployeeID())
                                        .fullName(Optional.ofNullable(merged.getFullName()).orElse("Unknown"))
                                        .summaryId(summaryId)
                                        .currency(merged.getCurrency() != null ? merged.getCurrency().getCode() : null)
                                        .exchangeInfo(merged.getExchangeInfo())
                                        .offCycleId(paymentComputeResponse.getOffCycleId())
                                        .departmentId(merged.getDepartmentID())
                                        .startDate(merged.getStartDate())
                                        .endDate(merged.getEndDate())
                                        .report(ReportUtils.serializeResponse(detailResponse))
                                        .createdDate(LocalDateTime.now())
                                        .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                                        .payrollStatus(paymentComputeResponse.isPayrollSimulation()
                                                ? PayrollStatus.SIMULATED
                                                : PayrollStatus.INITIATED)
                                        .offCycle(paymentComputeResponse.isOffCycle())
                                        .build();

                                return report;

                            } catch (Exception e) {
                                LOGGER.error("Error merging payment info group for companyId={}", companyId, e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .toList();

                // 🔹 Step 3: Save all merged reports in one batch
                if (!mergedReports.isEmpty()) {
                    payrollReportDetailRepo.saveAll(mergedReports);
                    LOGGER.info("Saved {} merged payroll report details for companyId={}", mergedReports.size(), companyId);
                } else {
                    LOGGER.warn("No payroll report details to save after merging for companyId={}", companyId);
                }

            } catch (Exception e) {
                LOGGER.error("Error while merging and saving payroll report details for companyId={}", companyId, e);
                throw new RuntimeException("Error while saving payroll report details", e);
            }
        });
    }

//    private void saveReportDetailsSimulate(PaymentComputeResponse paymentComputeResponse, String companyId) {
//
//        List<PaymentInfo> paymentInfoList = Optional.ofNullable(paymentComputeResponse.getReport())
//                .orElse(Collections.emptyList());
//
//        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
//            paymentInfoList.forEach(x -> {
//                try {
//                    PayrollReportDetailSimulate existingReport =
//                            payrollReportDetailRepoSimulate.findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateAndEndDateAndSummaryId(
//                                    companyId,
//                                    x.getEmployeeID(),
//                                    x.getStartDate(),
//                                    x.getEndDate(),
//                                    String.valueOf(paymentComputeResponse.getId())
//                            );
//
//                    PaymentInfo paymentInfoToSave = x;
//                    if (existingReport != null) {
//                        // safely unwrap old report or create empty PaymentInfo
//                        PaymentInfo oldPaymentInfo = Optional.ofNullable(ReportUtils.transform(existingReport))
//                                .map(r -> r.getDetail())
//                                .map(d -> d.getReport())
//                                .orElse(new PaymentInfo());
//                        //  merge maps safely
//                        boolean mapsDifferent = !Objects.equals(oldPaymentInfo.getPayeeTax(), x.getPayeeTax()) && !Objects.equals(oldPaymentInfo.getPension(), x.getPension());
//                        if (mapsDifferent) {
//                            oldPaymentInfo.setGrossPay(mergeMaps(oldPaymentInfo.getGrossPay(), paymentInfoToSave.getGrossPay()));
//                            oldPaymentInfo.setDeduction(mergeMaps(oldPaymentInfo.getDeduction(), x.getDeduction()));
//                            oldPaymentInfo.setTaxRelief(mergeMaps(oldPaymentInfo.getTaxRelief(), x.getTaxRelief()));
//                            oldPaymentInfo.setPayeeTax(mergeMaps(oldPaymentInfo.getPayeeTax(), x.getPayeeTax()));
//                            oldPaymentInfo.setEarning(mergeMaps(oldPaymentInfo.getEarning(), x.getEarning()));
//                            oldPaymentInfo.setNhf(mergeMaps(oldPaymentInfo.getNhf(), x.getNhf()));
//                            oldPaymentInfo.setOthers(mergeMaps(oldPaymentInfo.getOthers(), x.getOthers()));
//                            oldPaymentInfo.setPension(mergeMaps(oldPaymentInfo.getPension(), x.getPension()));
//                            oldPaymentInfo.setNetPay(oldPaymentInfo.getNetPay().add(paymentInfoToSave.getNetPay()));
//                            paymentInfoToSave = oldPaymentInfo;
//                        }
//                    }
//
//                    PayComputeDetailResponse payComputeDetailResponse = PayComputeDetailResponse.builder()
//                            .report(paymentInfoToSave)
//                            .build();
//
//                    // update existing report in-place or create new if null
//                    PayrollReportDetailSimulate payrollReportDetail = existingReport != null ? existingReport :
//                            PayrollReportDetailSimulate.builder()
//                                    .id(UUID.randomUUID().toString())
//                                    .build();
//
//                    payrollReportDetail.setEmployeeId(paymentInfoToSave.getEmployeeID());
//                    payrollReportDetail.setFullName(Optional.ofNullable(paymentInfoToSave.getFullName()).orElse("Unknown"));
//                    payrollReportDetail.setSummaryId(String.valueOf(paymentComputeResponse.getId()));
//                    payrollReportDetail.setCurrency(paymentInfoToSave.getCurrency() != null ?
//                            paymentInfoToSave.getCurrency().getCode() : null);
//                    payrollReportDetail.setExchangeInfo(Optional.ofNullable(paymentInfoToSave.getExchangeInfo()).orElse(null));
//                    payrollReportDetail.setCompanyId(companyId);
//                    payrollReportDetail.setDepartmentId(paymentInfoToSave.getDepartmentID());
//                    payrollReportDetail.setStartDate(paymentInfoToSave.getStartDate());
//                    payrollReportDetail.setEndDate(paymentInfoToSave.getEndDate());
//                    payrollReportDetail.setReport(ReportUtils.serializeResponse(payComputeDetailResponse));
//                    payrollReportDetail.setCreatedDate(LocalDateTime.now());
//                    payrollReportDetail.setPayrollSimulation(paymentComputeResponse.isPayrollSimulation());
//                    payrollReportDetail.setPayrollStatus(PayrollStatus.PENDING);
//                    payrollReportDetailRepoSimulate.save(payrollReportDetail);
//
//                } catch (Exception e) {
//                    LOGGER.error("Error processing report for employeeId={} startDate={} endDate={}",
//                            x.getEmployeeID(), x.getStartDate(), x.getEndDate(), e);
//                    throw e; // rethrow so CompletableFuture sees the error
//                }
//            });
//        });
//
//        try {
////            jobFuture.get();
//            jobFuture.join();
////        } catch (InterruptedException | ExecutionException e) {
//        } catch (CompletionException e) {
//            throw new RuntimeException("Error while saving payroll report details", e);
//        }
//    }
    /**
     * Builds a unique grouping key for merging.
     */
    private static String buildKey(String companyId, String employeeId, String startDate, String endDate, String summaryId) {
        return String.join("|", companyId, employeeId, startDate.toString(), endDate.toString(), summaryId);
    }

    /**
     * Merges two PaymentInfo objects safely.
     */
    private PaymentInfo mergePaymentInfos(PaymentInfo a, PaymentInfo b) {
        if (a == null) return b;
        if (b == null) return a;

        PaymentInfo merged = new PaymentInfo();
        merged.setEmployeeID(a.getEmployeeID());
        merged.setFullName(Optional.ofNullable(a.getFullName()).orElse(b.getFullName()));
        merged.setStartDate(a.getStartDate());
        merged.setEndDate(a.getEndDate());
        merged.setCurrency(a.getCurrency() != null ? a.getCurrency() : b.getCurrency());
        merged.setExchangeInfo(a.getExchangeInfo() != null ? a.getExchangeInfo() : b.getExchangeInfo());
        merged.setDepartmentID(Optional.ofNullable(a.getDepartmentID()).orElse(b.getDepartmentID()));

        merged.setGrossPay(mergeMaps(a.getGrossPay(), b.getGrossPay()));
        merged.setDeduction(mergeMaps(a.getDeduction(), b.getDeduction()));
        merged.setTaxRelief(mergeMaps(a.getTaxRelief(), b.getTaxRelief()));
        merged.setPayeeTax(mergeMaps(a.getPayeeTax(), b.getPayeeTax()));
        merged.setEarning(mergeMaps(a.getEarning(), b.getEarning()));
        merged.setNhf(mergeMaps(a.getNhf(), b.getNhf()));
        merged.setOthers(mergeMaps(a.getOthers(), b.getOthers()));
        merged.setPension(mergeMaps(a.getPension(), b.getPension()));

        // combine numeric values
        merged.setNetPay(
                Optional.ofNullable(a.getNetPay()).orElse(BigDecimal.ZERO)
                        .add(Optional.ofNullable(b.getNetPay()).orElse(BigDecimal.ZERO))
        );

        return merged;
    }

//    private void updateDashboardData(String updateType, PayrollReportSummary payrollReportSummary) {
//        LOGGER.info("Updating payroll report updateType : {}", updateType);
//
//        switch (updateType) {
//            case(AppConstants.payrollCountOffCycle) : dashboardDataService.updatePayrollCountTypeOffCycle(payrollReportSummary); break;
//            case(AppConstants.payrollCountRegular) : dashboardDataService.updatePayrollCountTypeRegular(payrollReportSummary); break;
//        }
//    }

    private void updateDashboardData(String updateType, PayrollReportSummary payrollReportSummary, boolean isRollback) {
        switch (updateType) {
            case(AppConstants.payrollCountOffCycle) : dashboardDataService.updatePayrollCountTypeOffCycle(payrollReportSummary, isRollback); break;
            case(AppConstants.payrollCountRegular) : dashboardDataService.updatePayrollCountTypeRegular(payrollReportSummary, isRollback); break;
        }
    }

    private String getStartDateRange(String dateStringStart, String dateStringEnd) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");  // Define the date format
        if(dateStringStart == null) {
            LocalDate date = LocalDate.parse(dateStringEnd, formatter);
            return date.minusMonths(11).format(formatter);
        } else {
            LocalDate date = LocalDate.parse(dateStringStart, formatter);
            return date.minusMonths(1).format(formatter);
        }
    }
    private String getEndDateRange(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");  // Define the date format

        // Parse the string into a LocalDate
        LocalDate date = LocalDate.parse(dateString, formatter);

        // Subtract one month from the date
        LocalDate result = date.plusMonths(1);

        // Convert the result back to a string
        return result.format(formatter);
    }

    private String generateReportCode(String startDate, boolean isOffCycle, long numberOfEmployees) {
        YearMonth ym = YearMonth.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String codeSuffix = ym.getYear() + "-" + ym.getMonth().getValue() + "-" + numberOfEmployees;
        return isOffCycle ? "PRO-"+ codeSuffix : "PRR-" + codeSuffix;
    }

    @Async
    protected void logGenerateReportEvent(String companyId, ReportResponse reportResponse) {
        var loggedInUserName = AuthUtil.getUserName();
        var loggedInUserEmail = AuthUtil.getUserEmail();
        var payPeriod = reportResponse.getStartDate() + " - " + reportResponse.getEndDate();
        auditTrailService.logEvent(AuditTrailEvents.GENERATE_REPORT, "Payroll for the pay period " + payPeriod + " was computed by " + loggedInUserName
                + " (" + loggedInUserEmail + ")", companyId);
    }

    private void logApproveReportEvent(String companyId, PayrollReportSummary reportResponse) {
        var loggedInUserName = AuthUtil.getUserName();
        var loggedInUserEmail = AuthUtil.getUserEmail();
        var payPeriod = reportResponse.getStartDate() + " - " + reportResponse.getEndDate();
        auditTrailService.logEvent(AuditTrailEvents.GENERATE_REPORT, "Payroll for the pay period " + payPeriod + " was approved by " + loggedInUserName
                + " (" + loggedInUserEmail + ")", companyId);
    }
    private void logPostReportToFinanceEvent(String companyId, PayrollReportSummary reportResponse) {
        var loggedInUserName = AuthUtil.getUserName();
        var loggedInUserEmail = AuthUtil.getUserEmail();

        var payPeriod = reportResponse.getStartDate() + " - " + reportResponse.getEndDate();
        auditTrailService.logEvent(AuditTrailEvents.POST_TO_FINANCE, "Payroll for the pay period " + payPeriod + " was posted to finance by " + loggedInUserName
                + " (" + loggedInUserEmail + ")", companyId);
    }

    public static Map<String, BigDecimal> mergeMaps(Map<String, BigDecimal> map1, Map<String, BigDecimal> map2) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (map1 != null) result.putAll(map1);
        if (map2 != null) {
            map2.forEach((key, value) -> {
                if (key != null && value != null) {
                    result.merge(key, value,
                            (oldVal, newVal) -> oldVal != null ? oldVal.add(newVal) : newVal
                    );
                }
            });
        }
        return result;
    }
}

