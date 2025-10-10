package com.xykine.computation.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.xykine.payroll.model.UserRole;
import java.util.Set;
@Data
public class EmployeeFilterRequest {
    private String firstName;
    private String lastName;
    @NotNull(message = "Company ID is required")
    private String companyID;
    private String departmentID;
    private String position;
    private String employeeIsLocked;
    private String active;
    private Set<UserRole> roles;
    private String email;
    private String phoneNumber;
    private String starDate;
    private String endDate;
    private int page = 0;
    private int size = 10;
}
