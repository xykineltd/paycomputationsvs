package com.xykine.computation.repo;

import com.xykine.computation.entity.Loan;
import com.xykine.computation.entity.PaymentSettingMetaData;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PaymentSettingMetadataRepo  extends MongoRepository<PaymentSettingMetaData, String> {
    List<PaymentSettingMetaData> findByEmployeeId(String employeeId);
}