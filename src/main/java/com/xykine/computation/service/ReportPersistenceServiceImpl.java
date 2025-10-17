package com.xykine.computation.service;

import com.xykine.computation.domain.JobStatus;
import com.xykine.computation.entity.*;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.repo.*;
import com.xykine.computation.repo.simulate.PayrollReportDetailSimulateRepo;
import com.xykine.computation.repo.simulate.PayrollReportSummarySimulateRepo;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.request.ReportByTypeRequest;
import com.xykine.computation.request.UpdateLoanRequest;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.*;

import com.xykine.computation.session.SessionCalculationObject;
import com.xykine.computation.utils.AuthUtil;
import com.xykine.computation.utils.OperationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xykine.payroll.model.*;


import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.xykine.computation.entity.simulate.PayrollReportDetailSimulate;
import com.xykine.computation.entity.simulate.PayrollReportSummarySimulate;
import com.xykine.computation.exceptions.PayrollReportNotException;
import com.xykine.computation.exceptions.PayrollUnmodifiableException;
import com.xykine.computation.utils.ReportUtils;
import com.xykine.computation.utils.AppConstants;

@Service
@RequiredArgsConstructor
public class ReportPersistenceServiceImpl implements ReportPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    private final AuditTrailService auditTrailService;
    private final PayrollReportSummaryRepo payrollReportSummaryRepo;
    private final PayrollReportSummarySimulateRepo payrollReportSummaryRepoSimulate;
    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final PayrollReportDetailSimulateRepo payrollReportDetailRepoSimulate;
    private final DashboardDataService dashboardDataService;
    private final YTDReportRepo ytdReportRepo;
    private final PayrollAsyncService payrollAsyncService;
    private final JobStatusStore jobStatusStore;
    private final ComputationConstantsRepo computationConstantsRepo;
    private final EmployeeMetadataService employeeMetadataService;
    private final AdminService adminService;
    private final ComputeService computeService;
    private final PayrollVarianceDetailsRepo payrollVarianceDetailsRepo;
    @Autowired
    private SessionCalculationObject sessionCalculationObject;

    @Override
    @Async
    public void computePayrollAsync(Consumer<JobStatusStore> progressCallback,
                                    String jobId, String authorizationHeader,
                                    PaymentInfoRequest paymentRequest) {
        jobStatusStore.updateJob(jobId, "IN_PROGRESS", "Computation started", "");
        progressCallback.accept(jobStatusStore);
        try {
            PayrollReportSummary payroll = payrollReportSummaryRepo
                    .findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(String.valueOf(paymentRequest.getStart()), paymentRequest.getCompanyId(), true);
            if (payroll != null) {
                jobStatusStore.updateJob(jobId, "COMPLETED", "Payroll computation complete", String.valueOf(payroll.getId()));
                progressCallback.accept(jobStatusStore);
                PayrollReportSummary payrollReportSummary = payrollReportSummaryRepo.findPayrollReportSummaryById(UUID.fromString(String.valueOf(payroll.getId())));
                payrollReportSummary.setPayrollSimulation(false);
                payrollReportSummary.setPayrollStatus(PayrollStatus.PENDING);
                payrollAsyncService.updateDetailStatusToPendingAsync(String.valueOf(payroll.getId()));
                return;
            }

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
            PaymentComputeResponse computeResponse = computeService.computePayroll(paymentInfoList);
            computeResponse = OperationUtils.refineResponse(computeResponse, sessionCalculationObject, paymentRequest);
            ReportResponse reportResponse = serializeAndSaveReport(computeResponse, paymentRequest.getCompanyId());
            jobStatusStore.updateJob(jobId, "COMPLETED", "Payroll computation complete", reportResponse.getReportId());
            progressCallback.accept(jobStatusStore);

        } catch (Exception e) {
            jobStatusStore.updateJob(jobId, "FAILED", e.getMessage(), "");
            progressCallback.accept(jobStatusStore);
        }
    }

    public ConcurrentHashMap<String, Set<SummaryDetail>> getSummaryVarianceDetails(String reportId, List<String> employeeIds, String header) {
        PayrollVarianceDetails payrollVarianceDetails = payrollVarianceDetailsRepo.findById(UUID.fromString(reportId)).orElse(null);
        ConcurrentHashMap<String, Set<SummaryDetail>> summaryVarianceDetails = new ConcurrentHashMap<>();
        if (payrollVarianceDetails == null) {
            return summaryVarianceDetails;
        }

        PayComputeVarianceDetails payComputeVarianceDetails = ReportUtils.transform(payrollVarianceDetails).getPayComputeVarianceDetails();
        summaryVarianceDetails = payComputeVarianceDetails.getSummaryDetailsVariance();

        String finalHeader = header;
        return summaryVarianceDetails.entrySet()
                .stream()
                .filter(entry -> finalHeader.equalsIgnoreCase(entry.getKey()))
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
        ReportResponse reportResponse = null;
        try {
//            if (paymentComputeResponse.isPayrollSimulation()) {
//                //delete and replace
//                payrollReportDetailRepoSimulate.deleteAll();
//                payrollReportSummaryRepoSimulate.deleteAll();
//                LOGGER.info("Simulated report with start date: " + paymentComputeResponse.getStart() + " will be saved.");
//                reportResponse = getReportResponseSimulate(paymentComputeResponse, companyId, paymentComputeResponse.getStart());
//            } else {
                //delete and replace based on pay period, ie only 1 pay period in the database and companyID
                deleteReportByDate(
                        paymentComputeResponse.getStart(),
                        companyId,
                        paymentComputeResponse.isOffCycle(),
                        false,
                        paymentComputeResponse.getOffCycleId()
                );
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

        PayComputeSummaryResponse payComputeSummaryResponse = PayComputeSummaryResponse.builder()
                .summary(paymentComputeResponse.getSummary())
                .summaryDetails(paymentComputeResponse.getSummaryDetails())
                .summaryVariance(processSummaryVariance(paymentComputeResponse.getSummary(), previousReportSummary))
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
                .payrollStatus(paymentComputeResponse.isPayrollSimulation() ? PayrollStatus.SIMULATED : PayrollStatus.PENDING)
                .paymentFrequency(paymentFrequency)
                .code(generateReportCode(paymentComputeResponse.getStart(), paymentComputeResponse.isOffCycle(), totalNumberOfEmployees))
                .build();

        payrollVarianceDetailsRepo.save(payrollVarianceDetails);
        payrollReportSummaryRepo.save(payrollReportSummary);
        saveReportDetails(paymentComputeResponse, companyId);
        return getPayRollReport(paymentComputeResponse.getId(), false);
    }

    private static ConcurrentHashMap<String, Set<SummaryDetail>> processSummaryDetailsVariance(
            ConcurrentHashMap<String, Set<SummaryDetail>> currentSummaryDetails,
            PayrollReportSummary previousPayrollReportSummary) {

        ConcurrentHashMap<String, Set<SummaryDetail>> summaryDetailsVariance = new ConcurrentHashMap<>();

        if (previousPayrollReportSummary == null) {
            return summaryDetailsVariance;
            // Previous null → variance = current values
//            currentSummaryDetails.forEach((key, currentDetails) -> {
//                Set<SummaryDetail> varianceDetails = Collections.newSetFromMap(new ConcurrentHashMap<>());
//                currentDetails.forEach(d -> {
//                    if (d.getValue().compareTo(BigDecimal.ZERO) != 0) {
//                        varianceDetails.add(d);
//                    }
//                });
//                if (!varianceDetails.isEmpty()) {
//                    summaryDetailsVariance.put(key, varianceDetails);
//                }
//            });
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

    private ConcurrentHashMap<String, Set<SummaryDetail>> processSummaryDetailsVarianceSimulate(
            ConcurrentHashMap<String, Set<SummaryDetail>> currentSummaryDetails) {

        ConcurrentHashMap<String, Set<SummaryDetail>> summaryDetailsVariance = new ConcurrentHashMap<>();

        currentSummaryDetails.forEach((key, detailsList) -> {
            Set<SummaryDetail> zeroValueDetails = Collections.synchronizedSet(new HashSet<>(
                    detailsList.stream()
                            .map(detail -> new SummaryDetail(
                                    detail.getEmployeeId(),
                                    detail.getEmployeeName(),
                                    detail.getDepartmentName(),
                                    detail.getValue(),
                                    BigDecimal.ZERO))
                            .toList()
            ));
            summaryDetailsVariance.put(key, zeroValueDetails);
        });

        return summaryDetailsVariance;
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

    private Map<String, BigDecimal> processSummaryVarianceSimulate(Map<String, BigDecimal> currentSummary) {
        Map<String, BigDecimal> summaryVariance = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : currentSummary.entrySet()) {
            summaryVariance.put(entry.getKey(), BigDecimal.ZERO);
        }
        return summaryVariance;
    }

    public ReportResponse getPayRollReport(UUID id) {
        PayrollReportSummarySimulate simulate = payrollReportSummaryRepoSimulate.findPayrollReportSummaryById(id);
        if (simulate != null) return ReportUtils.transform(simulate);
        PayrollReportSummary summary = payrollReportSummaryRepo.findPayrollReportSummaryById(id);
        if (summary != null) return ReportUtils.transform(summary);
        throw new RuntimeException("Report with id: " + id + " was not found");
    }


    public ReportResponse getPayRollReport(UUID id, boolean isSimulate) {
        if (isSimulate) {
            PayrollReportSummarySimulate payrollReportSimulateSummary = payrollReportSummaryRepoSimulate.findPayrollReportSummaryById(id);
            if (payrollReportSimulateSummary == null) {
                throw new RuntimeException("Report with id: " + id + " was not found");
            }
            return ReportUtils.transform(payrollReportSimulateSummary);
        } else {
            PayrollReportSummary payrollReportSummary = payrollReportSummaryRepo.findPayrollReportSummaryById(id);
            if (payrollReportSummary == null) {
                throw new RuntimeException("Report with id: " + id + " was not found");
            }
            return ReportUtils.transform(payrollReportSummary);
        }
    }

//    private ReportResponse getReportResponseSimulate(PaymentComputeResponse paymentComputeResponse, String companyId, String startDate) {
//        // TODO process the variance
//        PayComputeSummaryResponse payComputeSummaryResponse = PayComputeSummaryResponse.builder()
//                .summary(paymentComputeResponse.getSummary())
//                .summaryDetails(paymentComputeResponse.getSummaryDetails())
//                .summaryVariance(processSummaryVarianceSimulate(paymentComputeResponse.getSummary()))
//                .summaryDetailsVariance(processSummaryDetailsVarianceSimulate(paymentComputeResponse.getSummaryDetails()))
//                .build();
//        PayrollReportSummarySimulate payrollReportSummary = PayrollReportSummarySimulate.builder()
//                .id(paymentComputeResponse.getId())
//                .companyId(companyId)
//                .startDate(startDate)
//                .endDate(paymentComputeResponse.getEnd())
//                .report(ReportUtils.serializeResponse(payComputeSummaryResponse))
//                .createdDate(LocalDateTime.now())
//                .payrollStatus(PayrollStatus.PENDING)
//                .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
//                .build();
//        payrollReportSummaryRepoSimulate.save(payrollReportSummary);
//        saveReportDetailsSimulate(paymentComputeResponse, companyId);
//        return getPayRollReportSimulate(paymentComputeResponse.getStart());
//    }

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

        return retrievePayrolDetails(payrollReportDetailPage);
    }


    private List<ReportResponse> getPayRollReportOffCycle(String companyId) {
        return payrollReportSummaryRepo
                .findAllByCompanyIdAndPayrollSimulationAndOffCycle(companyId, false, true)
                .stream()
                .map(ReportUtils::transform)
                .collect(Collectors.toList());
    }

    public ReportResponse getPayRollReportSimulate(String starDate) {
        PayrollReportSummarySimulate payrollReportSummary = payrollReportSummaryRepoSimulate.findPayrollReportSummaryByStartDate(starDate);

        if (payrollReportSummary == null) {
            //return empty
            return new ReportResponse();
        }
        return ReportUtils.transform(payrollReportSummary);
    }

    //Pull both all report summary for display on dashboard
    public List<ReportResponse> getPayRollReports(String companyId) {
        List<ReportResponse> summary = getPayRollReportSimulates(companyId);
        var reports = payrollReportSummaryRepo.findAllByCompanyIdOrderByCreatedDateAsc(companyId).stream()
                .map(ReportUtils::transform).toList();
        summary.addAll(reports);
        //auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Pulled payroll report for company id :" + companyId);
        return summary;
    }

    @Override
    public List<ReportResponse> getPayRollReportsByStatus(String companyId, String status) {
        List<ReportResponse> reports = new ArrayList<>();
        if (status != null && status.equalsIgnoreCase("SIMULATED")) {
            reports = getPayRollReportSimulates(companyId);
        }
        // TODO create enum for this strings
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

        Map<String, Object> response = retrievePayrolDetails(payrollReportDetailPage);
        auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Pulled payroll report for company id :" + companyId + "and employee id: " + employeeID, companyId);
        return response;
    }

    @Override
    public Map<String, Object> getReportByEmployeeIDList(String companyId, List<String> employeeIDList, String summaryId, int page, int size) {
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndEmployeeIdInAndSummaryId(companyId, employeeIDList, summaryId, paging);
        Map<String, Object> response = retrievePayrolDetails(payrollReportDetailPage);
        return response;
    }

    private Map<String, Object> retrievePayrolDetails(Page<PayrollReportDetail> payrollReportDetailPage) {
        List<PayrollReportDetail> payrollDetails;
        payrollDetails = payrollReportDetailPage.getContent();
        List<ReportResponse> reportResponses = ReportUtils.transform(payrollDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("payrollDetails", reportResponses);
        response.put("currentPage", payrollReportDetailPage.getNumber());
        response.put("totalItems", payrollReportDetailPage.getTotalElements());
        response.put("totalPages", payrollReportDetailPage.getTotalPages());
        return response;
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

    private List<ReportResponse> getPayRollReportSimulates(String companyId) {
        return payrollReportSummaryRepoSimulate.findAllByCompanyIdOrderByCreatedDateAsc(companyId).stream()
                .map(ReportUtils::transform)
                .collect(Collectors.toList());
    }

    @Transactional
    public PayrollReportSummary approveReport(UpdateReportRequest request) {
        PayrollReportSummary existingSummaryReport;
        if (request.isOffCycle()) {
            existingSummaryReport = payrollReportSummaryRepo
                    .findPayrollReportSummaryByCompanyIdAndOffCycleId(request.getCompanyId(), request.getOffCycleId());
            updateDashboardData(AppConstants.payrollCountOffCycle, existingSummaryReport);
        } else {
            existingSummaryReport = payrollReportSummaryRepo
                    .findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(request.getStartDate(), request.getCompanyId(), false);
            LOGGER.info("===== existingSummaryReport {} ", existingSummaryReport);
            updateDashboardData(AppConstants.payrollCountRegular, existingSummaryReport);
        }
        existingSummaryReport.setPayrollStatus(request.getPayrollStatus());
        PayrollReportSummary reportResponse = payrollReportSummaryRepo.save(existingSummaryReport);

        logApproveReportEvent(request.getCompanyId(), reportResponse);
        if (request.getPayrollStatus().equals(PayrollStatus.APPROVED)) {
            payrollAsyncService.updateEmployeeLoanAsync(existingSummaryReport.getId().toString(), request.getCompanyId());
            payrollAsyncService.updateDetailStatusAsync(existingSummaryReport.getId().toString());
        }
        return existingSummaryReport;
    }

    public boolean deleteReport(UpdateReportRequest request) {
        auditTrailService.logEvent(AuditTrailEvents.DELETE_REPORT, "Deleted report with start date : " + request.getStartDate() + " company id : " + request.getCompanyId(), request.getCompanyId());
        return deleteReportByDate(request.getStartDate(),
                request.getCompanyId(),
                request.isOffCycle(),
                request.isCancelPayroll(),
                request.getOffCycleId()
        );
    }

    @Override
    public PayrollReportSummary completeReport(UpdateReportRequest request) {
        PayrollReportSummary existingSummaryReport;
        if (request.isOffCycle()) {
            existingSummaryReport = payrollReportSummaryRepo.findPayrollReportSummaryByStartDateAndCompanyIdAndOffCycleIdAndPayrollSimulation(
                    request.getStartDate(),
                    request.getCompanyId(),
                    request.getOffCycleId(),
                    false);
        } else {
            existingSummaryReport = payrollReportSummaryRepo
                    .findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(request.getStartDate(), request.getCompanyId(), false);
        }

        if (existingSummaryReport == null) {
            throw new RuntimeException("Unable to pull payroll report");
        }

        existingSummaryReport.setPayrollStatus(PayrollStatus.COMPLETED);
        var payrollReportSummary = payrollReportSummaryRepo.save(existingSummaryReport);
        logPostReportToFinanceEvent(request.getCompanyId(), payrollReportSummary);
        return existingSummaryReport;
    }

    private boolean deleteReportByDate(String startDate,
                                       String companyId,
                                       boolean isOffCycle,
                                       boolean isCancelPayroll,
                                       String offCycleId
    ) {
        if (isOffCycle && !isCancelPayroll) return false;
        //canceling offCycle payroll
        if (isOffCycle) {
            payrollReportSummaryRepo.deletePayrollReportSummaryByOffCycleIdAndCompanyId(offCycleId, companyId);
            payrollReportDetailRepo.deleteAllByOffCycleIdAndCompanyId(offCycleId, companyId);
            return true;
        }

        var payroll = payrollReportSummaryRepo
                .findPayrollReportSummaryByStartDateAndCompanyId(startDate, companyId);
        if (payroll != null && (payroll.getPayrollStatus().compareTo(PayrollStatus.APPROVED) == 0 || payroll.getPayrollStatus().compareTo(PayrollStatus.COMPLETED)  == 0)) {
            throw new PayrollUnmodifiableException(startDate);
        }

        //canceling regular payroll
        payrollReportSummaryRepo.deletePayrollReportSummaryByStartDateAndCompanyId(startDate, companyId);
        payrollReportDetailRepo.deleteAllByStartDateAndCompanyId(LocalDate.parse(startDate), companyId);
        return true;
    }


    @Override
    public Map<String, Object> getPaymentDetails(String summaryId, String companyId, String fullName, int page, int size) {
        List<PayrollReportDetail> payrollDetails = new ArrayList<>();
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailBySummaryIdAndCompanyIdAndFullNameContainingIgnoreCase(summaryId, companyId, fullName, paging);

        // if report detail is empty then check the simulated report detail table. No need for different endpoint.
        //TODO what if the payrollReportDetailPage above is not empty and we need to get the report for simulated payroll
        if (payrollReportDetailPage.isEmpty()) {
            payrollReportDetailPage = payrollReportDetailRepoSimulate
                    .findPayrollReportDetailBySummaryIdAndCompanyIdAndFullNameContainingIgnoreCase(summaryId, companyId, fullName, paging);
        }

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

    @Override
    public List<ReportAnalytics> getReportAnalytics(String companyId, int page, int size) {
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportSummary> payrollReportSummaryPage = payrollReportSummaryRepo.findPayrollReportSummaryByCompanyIdAndPayrollSimulationOrderByCreatedDateDesc(companyId, false, paging);
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
                    newReport.setEmployeeContributedPension(BigDecimal.ZERO);
                    newReport.setEmployerContributedPension(BigDecimal.ZERO);
                    return newReport;
                });
    }

    private void saveReportDetails(PaymentComputeResponse paymentComputeResponse,
                                   String companyId) {

        List<PaymentInfo> paymentInfoList = Optional.ofNullable(paymentComputeResponse.getReport())
                .orElse(Collections.emptyList());

        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            paymentInfoList.forEach(x -> {
                try {
                    PayrollReportDetail existingReport =
                            payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateAndEndDateAndSummaryId(
                                    companyId,
                                    x.getEmployeeID(),
                                    x.getStartDate(),
                                    x.getEndDate(),
                                    String.valueOf(paymentComputeResponse.getId()));

                    PaymentInfo paymentInfoToSave = x;
                    if (existingReport != null) {
                        // safely unwrap old report or create empty PaymentInfo
                        PaymentInfo oldPaymentInfo = Optional.ofNullable(ReportUtils.transform(existingReport))
                                .map(r -> r.getDetail())
                                .map(d -> d.getReport())
                                .orElse(new PaymentInfo());
                       //  merge maps safely
                        boolean mapsDifferent = !Objects.equals(oldPaymentInfo.getPayeeTax(), x.getPayeeTax()) && !Objects.equals(oldPaymentInfo.getPension(), x.getPension());
                        if (mapsDifferent) {
                            oldPaymentInfo.setGrossPay(mergeMaps(oldPaymentInfo.getGrossPay(), paymentInfoToSave.getGrossPay()));
                            oldPaymentInfo.setDeduction(mergeMaps(oldPaymentInfo.getDeduction(), x.getDeduction()));
                            oldPaymentInfo.setTaxRelief(mergeMaps(oldPaymentInfo.getTaxRelief(), x.getTaxRelief()));
                            oldPaymentInfo.setPayeeTax(mergeMaps(oldPaymentInfo.getPayeeTax(), x.getPayeeTax()));
                            oldPaymentInfo.setEarning(mergeMaps(oldPaymentInfo.getEarning(), x.getEarning()));
                            oldPaymentInfo.setNhf(mergeMaps(oldPaymentInfo.getNhf(), x.getNhf()));
                            oldPaymentInfo.setOthers(mergeMaps(oldPaymentInfo.getOthers(), x.getOthers()));
                            oldPaymentInfo.setPension(mergeMaps(oldPaymentInfo.getPension(), x.getPension()));
                            oldPaymentInfo.setNetPay(oldPaymentInfo.getNetPay().add(paymentInfoToSave.getNetPay()));
                            paymentInfoToSave = oldPaymentInfo;
                        }
                    }

                    PayComputeDetailResponse payComputeDetailResponse = PayComputeDetailResponse.builder()
                            .report(paymentInfoToSave)
                            .build();

                    // update existing report in-place or create new if null
                    PayrollReportDetail payrollReportDetail = existingReport != null ? existingReport :
                            PayrollReportDetail.builder()
                                    .id(UUID.randomUUID().toString())
                                    .build();

                    payrollReportDetail.setEmployeeId(paymentInfoToSave.getEmployeeID());
                    payrollReportDetail.setFullName(Optional.ofNullable(paymentInfoToSave.getFullName()).orElse("Unknown"));
                    payrollReportDetail.setSummaryId(String.valueOf(paymentComputeResponse.getId()));
                    payrollReportDetail.setCurrency(paymentInfoToSave.getCurrency() != null ?
                            paymentInfoToSave.getCurrency().getCode() : null);
                    payrollReportDetail.setExchangeInfo(Optional.ofNullable(paymentInfoToSave.getExchangeInfo()).orElse(null));
                    payrollReportDetail.setCompanyId(companyId);
                    payrollReportDetail.setOffCycleId(paymentComputeResponse.getOffCycleId());
                    payrollReportDetail.setDepartmentId(paymentInfoToSave.getDepartmentID());
                    payrollReportDetail.setStartDate(paymentInfoToSave.getStartDate());
                    payrollReportDetail.setEndDate(paymentInfoToSave.getEndDate());
                    payrollReportDetail.setReport(ReportUtils.serializeResponse(payComputeDetailResponse));
                    payrollReportDetail.setCreatedDate(LocalDateTime.now());
                    payrollReportDetail.setPayrollSimulation(paymentComputeResponse.isPayrollSimulation());
                    payrollReportDetail.setPayrollStatus(paymentComputeResponse.isPayrollSimulation() ? PayrollStatus.SIMULATED : PayrollStatus.PENDING);
                    payrollReportDetail.setOffCycle(paymentComputeResponse.isOffCycle());
                    payrollReportDetailRepo.save(payrollReportDetail);

                } catch (Exception e) {
                    LOGGER.error("Error processing report for employeeId={} startDate={} endDate={}",
                            x.getEmployeeID(), x.getStartDate(), x.getEndDate(), e);
                    throw e; // rethrow so CompletableFuture sees the error
                }
            });
        });

        try {
            jobFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error while saving payroll report details", e);
        }
    }

    private void saveReportDetailsSimulate(PaymentComputeResponse paymentComputeResponse, String companyId) {

        List<PaymentInfo> paymentInfoList = Optional.ofNullable(paymentComputeResponse.getReport())
                .orElse(Collections.emptyList());

        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            paymentInfoList.forEach(x -> {
                try {
                    PayrollReportDetailSimulate existingReport =
                            payrollReportDetailRepoSimulate.findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateAndEndDateAndSummaryId(
                                    companyId,
                                    x.getEmployeeID(),
                                    x.getStartDate(),
                                    x.getEndDate(),
                                    String.valueOf(paymentComputeResponse.getId())
                            );

                    PaymentInfo paymentInfoToSave = x;
                    if (existingReport != null) {
                        // safely unwrap old report or create empty PaymentInfo
                        PaymentInfo oldPaymentInfo = Optional.ofNullable(ReportUtils.transform(existingReport))
                                .map(r -> r.getDetail())
                                .map(d -> d.getReport())
                                .orElse(new PaymentInfo());
                        //  merge maps safely
                        boolean mapsDifferent = !Objects.equals(oldPaymentInfo.getPayeeTax(), x.getPayeeTax()) && !Objects.equals(oldPaymentInfo.getPension(), x.getPension());
                        if (mapsDifferent) {
                            oldPaymentInfo.setGrossPay(mergeMaps(oldPaymentInfo.getGrossPay(), paymentInfoToSave.getGrossPay()));
                            oldPaymentInfo.setDeduction(mergeMaps(oldPaymentInfo.getDeduction(), x.getDeduction()));
                            oldPaymentInfo.setTaxRelief(mergeMaps(oldPaymentInfo.getTaxRelief(), x.getTaxRelief()));
                            oldPaymentInfo.setPayeeTax(mergeMaps(oldPaymentInfo.getPayeeTax(), x.getPayeeTax()));
                            oldPaymentInfo.setEarning(mergeMaps(oldPaymentInfo.getEarning(), x.getEarning()));
                            oldPaymentInfo.setNhf(mergeMaps(oldPaymentInfo.getNhf(), x.getNhf()));
                            oldPaymentInfo.setOthers(mergeMaps(oldPaymentInfo.getOthers(), x.getOthers()));
                            oldPaymentInfo.setPension(mergeMaps(oldPaymentInfo.getPension(), x.getPension()));
                            oldPaymentInfo.setNetPay(oldPaymentInfo.getNetPay().add(paymentInfoToSave.getNetPay()));
                            paymentInfoToSave = oldPaymentInfo;
                        }
                    }

                    PayComputeDetailResponse payComputeDetailResponse = PayComputeDetailResponse.builder()
                            .report(paymentInfoToSave)
                            .build();

                    // update existing report in-place or create new if null
                    PayrollReportDetailSimulate payrollReportDetail = existingReport != null ? existingReport :
                            PayrollReportDetailSimulate.builder()
                                    .id(UUID.randomUUID().toString())
                                    .build();

                    payrollReportDetail.setEmployeeId(paymentInfoToSave.getEmployeeID());
                    payrollReportDetail.setFullName(Optional.ofNullable(paymentInfoToSave.getFullName()).orElse("Unknown"));
                    payrollReportDetail.setSummaryId(String.valueOf(paymentComputeResponse.getId()));
                    payrollReportDetail.setCurrency(paymentInfoToSave.getCurrency() != null ?
                            paymentInfoToSave.getCurrency().getCode() : null);
                    payrollReportDetail.setExchangeInfo(Optional.ofNullable(paymentInfoToSave.getExchangeInfo()).orElse(null));
                    payrollReportDetail.setCompanyId(companyId);
                    payrollReportDetail.setDepartmentId(paymentInfoToSave.getDepartmentID());
                    payrollReportDetail.setStartDate(paymentInfoToSave.getStartDate());
                    payrollReportDetail.setEndDate(paymentInfoToSave.getEndDate());
                    payrollReportDetail.setReport(ReportUtils.serializeResponse(payComputeDetailResponse));
                    payrollReportDetail.setCreatedDate(LocalDateTime.now());
                    payrollReportDetail.setPayrollSimulation(paymentComputeResponse.isPayrollSimulation());
                    payrollReportDetail.setPayrollStatus(PayrollStatus.PENDING);
                    payrollReportDetailRepoSimulate.save(payrollReportDetail);

                } catch (Exception e) {
                    LOGGER.error("Error processing report for employeeId={} startDate={} endDate={}",
                            x.getEmployeeID(), x.getStartDate(), x.getEndDate(), e);
                    throw e; // rethrow so CompletableFuture sees the error
                }
            });
        });

        try {
            jobFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error while saving payroll report details", e);
        }
    }

    private void updateDashboardData(String updateType, PayrollReportSummary payrollReportSummary) {
        switch (updateType) {
            case(AppConstants.payrollCountOffCycle) : dashboardDataService.updatePayrollCountTypeOffCycle(payrollReportSummary); break;
            case(AppConstants.payrollCountRegular) : dashboardDataService.updatePayrollCountTypeRegular(payrollReportSummary); break;
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
