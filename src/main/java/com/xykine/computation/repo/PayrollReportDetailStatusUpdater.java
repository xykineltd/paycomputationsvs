package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayrollReportDetailStatusUpdater {


    private final MongoTemplate mongoTemplate;

    public long updateAllStatuses(PayrollStatus status) {
        Query query = new Query(); // all docs
        Update update = new Update().set("payrollStatus", status);

        return mongoTemplate
                .updateMulti(query, update, PayrollReportDetail.class)
                .getModifiedCount();
    }

    public long updateStatusesByCompany(String companyId, PayrollStatus status) {
        Query query = Query.query(Criteria.where("companyId").is(companyId));
        Update update = new Update().set("payrollStatus", status);

        return mongoTemplate
                .updateMulti(query, update, PayrollReportDetail.class)
                .getModifiedCount();
    }

    public long updateStatusesByReportId(String reportId, PayrollStatus status) {
        Query query = Query.query(Criteria.where("reportId").is(reportId));
        Update update = new Update().set("payrollStatus", status);

        return mongoTemplate
                .updateMulti(query, update, PayrollReportDetail.class)
                .getModifiedCount();
    }

    public long updateStatusesByCompanyAndReport(String companyId,
                                                 String reportId,
                                                 PayrollStatus status) {
        Query query = Query.query(
                new Criteria().andOperator(
                        Criteria.where("companyId").is(companyId),
                        Criteria.where("summaryId").is(reportId)
                )
        );
        Update update = new Update().set("payrollStatus", status);

        return mongoTemplate
                .updateMulti(query, update, PayrollReportDetail.class)
                .getModifiedCount();
    }
}
