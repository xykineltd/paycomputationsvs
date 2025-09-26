package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import com.xykine.computation.repo.YTDReportRepo;
import com.xykine.computation.repo.simulate.PayrollReportDetailSimulateRepo;
import com.xykine.computation.repo.simulate.PayrollReportSummarySimulateRepo;
import com.xykine.computation.request.ReportByTypeRequest;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.*;

import com.xykine.computation.utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.simulate.PayrollReportDetailSimulate;
import com.xykine.computation.entity.simulate.PayrollReportSummarySimulate;
import com.xykine.computation.exceptions.PayrollReportNotException;
import com.xykine.computation.exceptions.PayrollUnmodifiableException;
import com.xykine.computation.utils.ReportUtils;
import com.xykine.computation.utils.AppConstants;

@Slf4j
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
    private final PayrollReportDetailSimulateRepo payrollReportDetailSimulateRepo;

    @Transactional
    public ReportResponse serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse, String companyId)
            throws IOException {
        long startTime = System.currentTimeMillis();
        ReportResponse reportResponse = null;
        try {
            if (paymentComputeResponse.isPayrollSimulation()) {
                //delete and replace
                payrollReportDetailRepoSimulate.deleteAll();
                payrollReportSummaryRepoSimulate.deleteAll();
                LOGGER.info("Simulated report with start date: " + paymentComputeResponse.getStart() + " will be saved.");
                reportResponse = getReportResponseSimulate(paymentComputeResponse, companyId, paymentComputeResponse.getStart());
            } else {
                //delete and replace based on pay period, ie only 1 pay period in the database and companyID
                deleteReportByDate(
                        paymentComputeResponse.getStart(),
                        companyId,
                        paymentComputeResponse.isOffCycle(),
                        false,
                        paymentComputeResponse.getOffCycleId()
                );
                reportResponse = getReportResponse(paymentComputeResponse, companyId, paymentComputeResponse.getStart());
            }
        } catch (RuntimeException e) {
            LOGGER.info(" exception {} ", e.toString());
            throw e;
        }
        long endTime = System.currentTimeMillis();
        LOGGER.info(" Process time {} ms", endTime - startTime);
        logGenerateReportEvent(companyId, reportResponse);
        return reportResponse;
    }
    private void populateReportVariance(PaymentComputeResponse paymentComputeResponse) {
        paymentComputeResponse.setSummaryVariance(paymentComputeResponse.getSummary());
        paymentComputeResponse.setSummaryDetailsVariance(paymentComputeResponse.getSummaryDetails());
    }

    private ReportResponse getReportResponse(PaymentComputeResponse paymentComputeResponse, String companyId, String startDate) {
        var previousDate = LocalDate.parse(startDate).minusMonths(1);
        var previousReportSummary = payrollReportSummaryRepo.findPayrollReportSummaryByStartDateAndCompanyId(previousDate.toString(), companyId);
        PaymentInfo paymentInfo = paymentComputeResponse.getReport().get(0);
        long totalNumberOfEmployees = paymentInfo.getTotalNumberOfEmployees();
        PaymentFrequencyEnum paymentFrequency = paymentInfo.getSalaryFrequency();

        PayComputeSummaryResponse payComputeSummaryResponse = PayComputeSummaryResponse.builder()
                .summary(paymentComputeResponse.getSummary())
                .summaryDetails(paymentComputeResponse.getSummaryDetails())
                .summaryVariance(processSummaryVariance(paymentComputeResponse.getSummary(), previousReportSummary))
                .summaryDetailsVariance(processSummaryDetailsVariance(paymentComputeResponse.getSummaryDetails(), previousReportSummary))
                .build();

        PayrollReportSummary payrollReportSummary = PayrollReportSummary.builder()
                .id(paymentComputeResponse.getId())
                .companyId(companyId)
                .offCycleId(paymentComputeResponse.getOffCycleId())
                .startDate(startDate)
                .endDate(paymentComputeResponse.getEnd())
                .report(ReportUtils.serializeResponse(payComputeSummaryResponse))
                .createdDate(LocalDateTime.now())
                .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                .offCycle(paymentComputeResponse.isOffCycle())
                .totalNumberOfEmployees(totalNumberOfEmployees)
                .payrollStatus(PayrollStatus.PENDING)
                .paymentFrequency(paymentFrequency)
                .code(generateReportCode(startDate, paymentComputeResponse.isOffCycle(), totalNumberOfEmployees))
                .build();
        payrollReportSummaryRepo.save(payrollReportSummary);
        saveReportDetails(paymentComputeResponse, companyId, PayrollStatus.PENDING);
        return getPayRollReport(paymentComputeResponse.getId(), false);
    }


    private ConcurrentHashMap<String, List<SummaryDetail>> processSummaryDetailsVariance(
            ConcurrentHashMap<String, List<SummaryDetail>> currentSummaryDetails,
            PayrollReportSummary previousPayrollReportSummary) {

        ConcurrentHashMap<String, List<SummaryDetail>> summaryDetailsVariance = new ConcurrentHashMap<>();

        if (previousPayrollReportSummary == null) {
            // If previousPayrollReportSummary is null, set all values to zero
            //    summaryDetailsVariance.put("NONE", new ArrayList<>());

        } else {
            // If previousPayrollReportSummary is not null, calculate the differences
            var previousSummaryDetails = ReportUtils.transform(previousPayrollReportSummary)
                    .getSummary()
                    .getSummaryDetails();

            currentSummaryDetails.forEach((key, currentDetailsList) -> {
                List<SummaryDetail> previousDetailsList = previousSummaryDetails.get(key);

                // Map from employee name to previous details for quick lookup
                Map<String, SummaryDetail> previousDetailsMap = previousDetailsList != null
                        ? previousDetailsList.stream()
                        .collect(Collectors.toMap(SummaryDetail::getEmployeeId, detail -> detail))
                        : new HashMap<>();

                // Calculate the differences
                List<SummaryDetail> varianceDetailsList = Collections.synchronizedList(new ArrayList<>(
                        currentDetailsList.stream()
                                .map(detail -> {
                                    SummaryDetail previousDetail = previousDetailsMap.get(detail.getEmployeeId());
                                    BigDecimal previousValue = previousDetail != null ? previousDetail.getValue() : BigDecimal.ZERO;
                                    BigDecimal difference = detail.getValue().subtract(previousValue);
                                    return new SummaryDetail(detail.getEmployeeId(), detail.getEmployeeName(), detail.getDepartmentName(), difference);
                                })
                                .filter(x -> x.getValue().compareTo(BigDecimal.ZERO) != 0)
                                .toList()
                ));

                summaryDetailsVariance.put(key, varianceDetailsList);
            });
        }

        return summaryDetailsVariance;
    }



    private ConcurrentHashMap<String, List<SummaryDetail>> processSummaryDetailsVarianceSimulate(
            ConcurrentHashMap<String, List<SummaryDetail>> currentSummaryDetails) {

        ConcurrentHashMap<String, List<SummaryDetail>> summaryDetailsVariance = new ConcurrentHashMap<>();

        currentSummaryDetails.forEach((key, detailsList) -> {
            List<SummaryDetail> zeroValueDetails = Collections.synchronizedList(new ArrayList<>(
                    detailsList.stream()
                            .map(detail -> new SummaryDetail(
                                    detail.getEmployeeId(),
                                    detail.getEmployeeName(),
                                    detail.getDepartmentName(),
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

    private ReportResponse getReportResponseSimulate(PaymentComputeResponse paymentComputeResponse, String companyId, String startDate) {
        // TODO process the variance
        PayComputeSummaryResponse payComputeSummaryResponse = PayComputeSummaryResponse.builder()
                .summary(paymentComputeResponse.getSummary())
                .summaryDetails(paymentComputeResponse.getSummaryDetails())
                // TODO update the variance values
                .summaryVariance(processSummaryVarianceSimulate(paymentComputeResponse.getSummary()))
                .summaryDetailsVariance(processSummaryDetailsVarianceSimulate(paymentComputeResponse.getSummaryDetails()))
                .build();
        PayrollReportSummarySimulate payrollReportSummary = PayrollReportSummarySimulate.builder()
                .id(paymentComputeResponse.getId())
                .companyId(companyId)
                .startDate(startDate)
                .endDate(paymentComputeResponse.getEnd())
                .report(ReportUtils.serializeResponse(payComputeSummaryResponse))
                .createdDate(LocalDateTime.now())
                .payrollStatus(PayrollStatus.SIMULATED)
                .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                .build();
        payrollReportSummaryRepoSimulate.save(payrollReportSummary);
        saveReportDetailsSimulate(paymentComputeResponse, companyId);
        return getPayRollReportSimulate(paymentComputeResponse.getStart());
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
//    public List<ReportResponse> getPayRollReports(String companyId, int page, int size) {
//        List<ReportResponse> allReports = getPayRollReportSimulates(companyId);
//        var reports = payrollReportSummaryRepo.findAllByCompanyIdOrderByCreatedDateAsc(companyId).stream()
//                .map(ReportUtils::transform).toList();
//        allReports.addAll(reports);
//        //auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Pulled payroll report for company id :" + companyId);
//        return allReports;
//    }

//    @Override
//    public Map<String, Object> getPayRollReports(String companyId, int page, int size) {
//        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdDate"));
//
//        Page<PayrollReportSummary> reportsPage =
//                payrollReportSummaryRepo.findAllByCompanyIdOrderByCreatedDateAsc(companyId, pageable);
//
//        List<ReportResponse> reports = reportsPage.getContent()
//                .stream()
//                .map(ReportUtils::transform)
//                .toList();
//
//        // Keep simulations separate to avoid skewing pagination counts
//        List<ReportResponse> simulatedReports = getPayRollReportSimulates(companyId);
//
//        Map<String, Object> response = new HashMap<>();
//        response.put("reports", reports);
//        response.put("simulatedReports", simulatedReports);
//        response.put("currentPage", reportsPage.getNumber());
//        response.put("totalItems", reportsPage.getTotalElements());
//        response.put("totalPages", reportsPage.getTotalPages());
//        response.put("pageSize", reportsPage.getSize());
//        return response;
//    }


    @Override
    public Map<String, Object> getPayRollReports(String companyId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size > 0 ? size : 10;

        // Fetch all persisted reports for the company (unpaged)
        Page<PayrollReportSummary> persistedPage =
                payrollReportSummaryRepo.findAllByCompanyIdOrderByCreatedDateAsc(companyId, Pageable.unpaged());

        List<ReportResponse> persisted = persistedPage.getContent()
                .stream()
                .map(ReportUtils::transform)
                .toList();

        // Fetch all simulated reports
        List<ReportResponse> simulated = getPayRollReportSimulates(companyId);

        // Merge both, sort by createdDate ASC
        List<ReportResponse> combined = new ArrayList<>(persisted.size() + simulated.size());
        combined.addAll(persisted);
        combined.addAll(simulated);

        combined.sort(Comparator.comparing(
                rr -> parseCreated(rr.getCreatedDate()),
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        // In-memory pagination
        int totalItems = combined.size();
        int fromIndex = Math.min(safePage * safeSize, totalItems);
        int toIndex = Math.min(fromIndex + safeSize, totalItems);
        List<ReportResponse> pageContent = combined.subList(fromIndex, toIndex);

        int totalPages = (int) Math.ceil((double) totalItems / safeSize);

        Map<String, Object> response = new HashMap<>();
        response.put("reports", pageContent);
        response.put("currentPage", safePage);
        response.put("totalItems", totalItems);
        response.put("totalPages", totalPages);
        response.put("pageSize", safeSize);
        return response;
    }

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

            updateDashboardData(AppConstants.payrollCountRegular, existingSummaryReport);
        }
        existingSummaryReport.setPayrollStatus(request.getPayrollStatus());
        var reportResponse = payrollReportSummaryRepo.save(existingSummaryReport);
        //TODO update the detail report once the payroll is approved
        logApproveReportEvent(request.getCompanyId(), reportResponse);
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

    /*
    @Override
    public List<ReportAnalytics> getReportAnalytics(String companyId) {

        var regularPayrolls =  generateDateFromJanToDecember().stream().map(
                date -> getReportAnalytics(getPayRollReport(date.toString(), companyId), companyId)
        ).filter( r -> r.getReportId() != null)
                .toList();

        // get for the offCyclePayrolls
        var offCyclePayrolls =  getPayRollReportOffCycle(companyId).stream().map(
                        reportResponse -> getReportAnalytics(reportResponse, companyId)
                ).filter( r -> r.getReportId() != null)
                .toList();


        List<ReportAnalytics> mergedList = new ArrayList<>(regularPayrolls);

        mergedList.addAll(offCyclePayrolls);
        //auditTrailService.logEvent(AuditTrailEvents.RETRIEVE_REPORT, "Get report analytics for company id :" +  companyId);
        return mergedList;
    }
     */

    @Override
    public List<ReportAnalytics> getReportAnalytics(String companyId, int page, int size) {
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportSummary> payrollReportSummaryPage = payrollReportSummaryRepo.findPayrollReportSummaryByCompanyIdAndPayrollSimulationOrderByCreatedDateDesc(companyId, false, paging);
        log.info(" ======> payrollReportSummaryPage list {}", payrollReportSummaryPage.getTotalElements());
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
                                   String companyId,
                                   PayrollStatus status) {

        List<PaymentInfo> paymentInfoList = Optional.ofNullable(paymentComputeResponse.getReport())
                .orElse(Collections.emptyList());

        CompletableFuture<Void> jobFuture = CompletableFuture.runAsync(() -> {
            paymentInfoList.forEach(x -> {
                try {
                    PayrollReportDetail existingReport =
                            payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateAndEndDate(
                                    companyId,
                                    x.getEmployeeID(),
                                    x.getStartDate(),
                                    x.getEndDate()
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
                    payrollReportDetail.setPayrollStatus(status);
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
                            payrollReportDetailRepoSimulate.findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateAndEndDate(
                                    companyId,
                                    x.getEmployeeID(),
                                    x.getStartDate(),
                                    x.getEndDate()
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

    private void logGenerateReportEvent(String companyId, ReportResponse reportResponse) {
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
