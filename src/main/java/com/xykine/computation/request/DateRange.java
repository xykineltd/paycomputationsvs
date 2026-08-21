package com.xykine.computation.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DateRange {
    private LocalDate start;
    private LocalDate end;
}
