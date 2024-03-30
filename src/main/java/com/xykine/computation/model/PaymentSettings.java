package com.xykine.computation.model;

import com.xykine.computation.model.enums.PaymentTypeEnum;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PaymentSettings implements Serializable {
        private String paymentSettingID;
        private String employeeID;
        private PaymentTypeEnum type;
        private String name;
        private BigDecimal value;
        private boolean active;
        private boolean pensionable;
        private String createdDate;
        private String lastModifiedDate;
        private String createdBy;
        private String lastModifiedBy;
        private int version;
        private boolean prorated;
}