package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.PayrollType;
import com.xykine.computation.request.ReportFilterRequest;
import com.xykine.computation.response.PaginatedReportSummaryResponse;
import com.xykine.computation.response.PayComputeSummaryResponse;
import com.xykine.computation.response.ReportSummaryResponse;
import com.xykine.computation.utils.ReportUtils;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.xykine.payroll.model.MapKeys;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Repository
public class PayrollReportSummaryCustomFilter {

    private final MongoTemplate mongoTemplate;

    public PaginatedReportSummaryResponse filterReports(ReportFilterRequest filter) {

        List<Criteria> criteriaList = new ArrayList<>();

        //  Always required (also annotated @NotNull in request)
        if (filter.getCompanyId() != null && !filter.getCompanyId().trim().isEmpty()) {
            criteriaList.add(Criteria.where("companyId").is(filter.getCompanyId().trim()));
        }

        // ✅ payrollType → offCycle mapping
        if (filter.getPayrollType() != null) {
            boolean offCycle = filter.getPayrollType() != PayrollType.REGULAR;
            criteriaList.add(
                    Criteria.where("offCycle").is(offCycle)
            );
        }

        //  payrollStatus (optional)
        if (filter.getPayrollStatus() != null) {
            criteriaList.add(Criteria.where("payrollStatus").is(filter.getPayrollStatus()));
        }

        //  startDate / endDate filtering (strings; assumes ISO format YYYY-MM-DD)
        // If both provided -> inclusive range
        // If only one provided -> exact match (or you can choose gte/lte behavior)
        String startDate = filter.getStartDate() != null ? filter.getStartDate().trim() : null;
        String endDate   = filter.getEndDate() != null ? filter.getEndDate().trim() : null;

        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            // inclusive: startDate >= startDate AND endDate <= endDate
            // (common interpretation: report period falls within requested window)
            criteriaList.add(Criteria.where("startDate").gte(startDate));
            criteriaList.add(Criteria.where("endDate").lte(endDate));

            // If instead you want "startDate BETWEEN start and end" ONLY:
            // criteriaList.add(Criteria.where("startDate").gte(startDate).lte(endDate));
        } else if (startDate != null && !startDate.isEmpty()) {
            criteriaList.add(Criteria.where("startDate").is(startDate));
        } else if (endDate != null && !endDate.isEmpty()) {
            criteriaList.add(Criteria.where("endDate").is(endDate));
        }

        Query query;
        Query countQuery;

        if (criteriaList.isEmpty()) {
            query = new Query();
            countQuery = new Query();
        } else {
            Criteria combined = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
            query = new Query(combined);
            countQuery = new Query(combined);
        }

        // Pagination + Sort (newest first is typical for reports)
        Pageable pageable = PageRequest.of(
                Math.max(filter.getPage(), 0),
                Math.max(filter.getSize(), 1),
                Sort.by(Sort.Direction.DESC, "createdDate")
        );
        query.with(pageable);

        // Optional: collation (mostly useful for string sorts; harmless here)
        Collation ci = Collation.of("en").strength(Collation.ComparisonLevel.secondary());
        query.collation(ci);
        countQuery.collation(ci);

        List<PayrollReportSummary> summaries =
                mongoTemplate.find(query, PayrollReportSummary.class);

        long total =
                mongoTemplate.count(countQuery, PayrollReportSummary.class);

        // ✅ Map directly to ReportSummaryResponse
        List<ReportSummaryResponse> responses = summaries.stream()
                .map(this::toSummaryResponse)
                .toList();

        return PaginatedReportSummaryResponse.builder()
                .currentPage(pageable.getPageNumber())
                .totalItems(total)
                .totalPages((int) Math.ceil((double) total / pageable.getPageSize()))
                .items(responses)
                .build();
    }

    private ReportSummaryResponse toSummaryResponse(PayrollReportSummary s) {
        PayComputeSummaryResponse summaryResponse =  SerializationUtils.deserialize(s.getReport());

        return ReportSummaryResponse.builder()
                .reportId(s.getId().toString())
                .companyId(s.getCompanyId())
                .offCycleId(s.getOffCycleId())
                .payrollStatus(s.getPayrollStatus().name())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .createdDate(
                        s.getCreatedDate() != null
                                ? s.getCreatedDate().toString()
                                : null
                )
                .payrollSimulated(s.isPayrollSimulation())
                .offCycle(s.isOffCycle())
                .totalNumberOfEmployees(s.getTotalNumberOfEmployees())
                .code(s.getCode())
                .grossPay(summaryResponse.getSummary().get(MapKeys.TOTAL_GROSS_PAY))
                .build();
    }
}

