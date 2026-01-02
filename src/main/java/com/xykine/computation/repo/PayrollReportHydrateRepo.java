package com.xykine.computation.repo;


import com.xykine.computation.dto.PagedResult;
import com.xykine.computation.dto.PayrollReportRow;
import org.bson.Document;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.SerializationUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
public class PayrollReportHydrateRepo {

    private final MongoTemplate mongoTemplate;

    private static final String PRD_COL = "payrollReportDetail";
    private static final String EMP_COL = "employee";

    public PayrollReportHydrateRepo(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public PagedResult<PayrollReportRow> searchPagedAndHydrateReport(
            String companyId,
            String summaryId,
            String departmentId,
            LocalDate hireStartDate,
            LocalDate hireEndDate,
            String employeeName,
            String employeeEmail,
            Boolean employeeStatus,
            String position,
            Pageable pageable
    ) {

        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.max(Math.min(pageable.getPageSize(), 200), 1);
        long skip = (long) page * size;

        // --------------------
        // Build PRD match
        // --------------------
        List<Criteria> prdCriteria = new ArrayList<>();
        if (StringUtils.hasText(companyId)) prdCriteria.add(Criteria.where("companyId").is(companyId));
        if (StringUtils.hasText(summaryId)) prdCriteria.add(Criteria.where("summaryId").is(summaryId));
        if (StringUtils.hasText(departmentId)) prdCriteria.add(Criteria.where("departmentId").is(departmentId));

        Criteria prdMatch = prdCriteria.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(prdCriteria.toArray(new Criteria[0]));

        // --------------------
        // Join employee
        // --------------------
        LookupOperation lookupEmployee = LookupOperation.newLookup()
                .from(EMP_COL)
                .localField("employeeId")
                .foreignField("_id")
                .as("employee");

        UnwindOperation unwindEmployee = unwind("employee", true);

        // --------------------
        // Employee filters
        // --------------------
        List<Criteria> empCriteria = new ArrayList<>();

        if (hireStartDate != null) empCriteria.add(Criteria.where("employee.startDate").gte(hireStartDate));
        if (hireEndDate != null)   empCriteria.add(Criteria.where("employee.startDate").lte(hireEndDate));
        if (StringUtils.hasText(position)) empCriteria.add(Criteria.where("employee.position").is(position));
        if (employeeStatus != null) empCriteria.add(Criteria.where("employee.active").is(employeeStatus));

        if (StringUtils.hasText(employeeEmail)) {
            Pattern re = Pattern.compile(Pattern.quote(employeeEmail.trim()), Pattern.CASE_INSENSITIVE);
            empCriteria.add(new Criteria().orOperator(
                    Criteria.where("employee.email").regex(re),
                    Criteria.where("employee.officialEmail").regex(re)
            ));
        }

        if (StringUtils.hasText(employeeName)) {
            Pattern re = Pattern.compile(Pattern.quote(employeeName.trim()), Pattern.CASE_INSENSITIVE);
            empCriteria.add(new Criteria().orOperator(
                    Criteria.where("employee.fullName").regex(re),
                    Criteria.where("employee.firstName").regex(re),
                    Criteria.where("employee.lastName").regex(re)
            ));
        }

        Criteria empMatch = empCriteria.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(empCriteria.toArray(new Criteria[0]));

        // --------------------
        // Slim projection BEFORE sort (do NOT include report)
        // Create alias employeeFullName here so we can sort by it.
        // --------------------
        ProjectionOperation slimProject = project()
                .and("_id").as("id")
                .and("summaryId").as("summaryId")
                .and("offCycleId").as("offCycleId")
                .and("companyId").as("companyId")
                .and("departmentId").as("departmentId")
                .and("employeeId").as("employeeId")
                .and("startDate").as("startDate")
                .and("endDate").as("endDate")
                .and("payrollStatus").as("payrollStatus")
                .and("offCycle").as("offCycle")
                .and("payrollSimulation").as("payrollSimulation")
                .and("createdDate").as("createdDate")

                // employee fields (returned + used for sort)
                .and("employee.fullName").as("employeeFullName")
                .and("employee.employeeCode").as("employeeCode")
                .and("employee.startDate").as("hireDate");

        // --------------------
        // RAW $sort to bypass "Invalid reference" issues
        // Default: ASC by employeeFullName like your Pageable example
        // --------------------
//        Sort.Direction dir = Sort.Direction.ASC;
//        if (pageable.getSort().isSorted()) {
//            // if user passes Sort.by("fullName") ASC/DESC, respect that
//            for (Sort.Order o : pageable.getSort()) {
//                if ("fullName".equalsIgnoreCase(o.getProperty())) {
//                    dir = o.getDirection();
//                    break;
//                }
//            }
//        }


        int sortDir = pageable.getSort().getOrderFor("fullName") != null &&
                pageable.getSort().getOrderFor("fullName").isDescending()
                ? -1
                : 1;

        AggregationOperation rawSort = context -> new Document("$sort",
                new Document("employeeFullName", sortDir)
                        .append("id", 1)
        );

//
//
//
//
//        AggregationOperation rawSort = context -> new Document("$sort",
//                new Document("employeeFullName", dir.isAscending() ? 1 : -1)
//                        .append("id", 1) // stable tie-breaker
//        );

        // --------------------
        // Facet: pagination + total
        // (Since we already projected, facet data only needs skip+limit)
        // --------------------
        FacetOperation facet = facet(
                skip(skip),
                limit(size)
        ).as("data").and(
                count().as("total")
        ).as("meta");

        AggregationOptions opts = AggregationOptions.builder()
                .allowDiskUse(true)
                .build();

        Aggregation agg = newAggregation(
                match(prdMatch),
                lookupEmployee,
                unwindEmployee,
                match(empMatch),
                slimProject,
                rawSort,
                facet
        ).withOptions(opts);

        Document step1 = mongoTemplate.aggregate(agg, PRD_COL, Document.class).getUniqueMappedResult();

        if (step1 == null) return PagedResult.of(List.of(), page, size, 0);

        List<Document> meta = (List<Document>) step1.getOrDefault("meta", List.of());
        long total = meta.isEmpty() ? 0 : ((Number) meta.get(0).getOrDefault("total", 0)).longValue();

        List<Document> rows = (List<Document>) step1.getOrDefault("data", List.of());
        if (rows.isEmpty()) return PagedResult.of(List.of(), page, size, total);

        List<String> ids = rows.stream().map(d -> d.getString("id")).filter(Objects::nonNull).toList();

        // --------------------
        // STEP 2: Fetch report bytes only for these IDs, then deserialize
        // --------------------
        Query q = Query.query(Criteria.where("_id").in(ids));
        q.fields().include("_id").include("report");

        List<Document> reportDocs = mongoTemplate.find(q, Document.class, PRD_COL);

        Map<String, Object> idToReportData = new HashMap<>();
        for (Document rd : reportDocs) {
            String id = rd.getString("_id");
            Object bytesObj = rd.get("report");

            Object reportData = null;
            if (bytesObj instanceof byte[] bytes && bytes.length > 0) {
                reportData = SerializationUtils.deserialize(bytes);
            }
            idToReportData.put(id, reportData);
        }

        // --------------------
        // Merge + map to DTO (preserve sorted order from step1)
        // --------------------
        List<PayrollReportRow> out = rows.stream().map(d -> {
            PayrollReportRow r = new PayrollReportRow();
            r.setId(d.getString("id"));
            r.setSummaryId(d.getString("summaryId"));
            r.setOffCycleId(d.getString("offCycleId"));
            r.setCompanyId(d.getString("companyId"));
            r.setDepartmentId(d.getString("departmentId"));
            r.setEmployeeId(d.getString("employeeId"));

            r.setEmployeeFullName(d.getString("employeeFullName"));
            r.setEmployeeCode(d.getString("employeeCode"));

            // hireDate conversion (LocalDate can come back as Date or String depending on storage)
            Object hd = d.get("hireDate");
            if (hd instanceof String s) {
                r.setHireDate(LocalDate.parse(s));
            } else if (hd instanceof Date dt) {
                r.setHireDate(dt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            } else if (hd instanceof LocalDate ld) {
                r.setHireDate(ld);
            }

            r.setStartDate(d.getString("startDate"));
            r.setEndDate(d.getString("endDate"));
            r.setPayrollStatus(d.get("payrollStatus"));
            r.setOffCycle(Boolean.TRUE.equals(d.getBoolean("offCycle")));
            r.setPayrollSimulation(Boolean.TRUE.equals(d.getBoolean("payrollSimulation")));

            r.setReportData(idToReportData.get(r.getId()));
            return r;
        }).collect(Collectors.toList());

        return PagedResult.of(out, page, size, total);
    }
}
