package com.xykine.computation.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PaymentSettings implements Serializable {
        private Long paymentSettingID;
        private Long employeeID;
        private String type;
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

        // Constructors, methods, etc. (Lombok will generate these)
}