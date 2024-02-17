package com.xykine.computation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.util.Date;

@Entity
public class T511K {
    @Id
    private String constant;
    private String description;
    private Date startDate;
    private Date endDate;
    private BigDecimal amount;
}
