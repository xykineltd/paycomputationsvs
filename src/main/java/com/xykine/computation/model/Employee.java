package com.xykine.computation.model;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Set;

@Data
public class Employee implements Serializable {

    private Set<PaymentSettings> paymentSettings;

}