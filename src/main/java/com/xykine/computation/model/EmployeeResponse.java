package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.StreamSupport;

public record EmployeeResponse(
        Long employeeID,
        String firstName,
        String lastName,
        String middleName,
        String fullName,
        String employeeCode,
        String employeeExternalCode,
        String title,
        String maritalStatus,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String lga,
        String postalCode,
        String dob,
        String phone1,
        String phone2,
        String email,
        String officialEmail,
        String gender,
        String idType,
        String managerID,
        String isManager,
        String employeeGroupID,
        String employeeSubGroupID,
        String grade,
        String level,
        String contractType,
        String startDate,
        String endDate,
        String position,
        String departmentID,
        String unitID,
        String dimensionID,
        String costCenterID,
        String currencyID,
        String pfaID,
        String bankID,
        String salaryFrequency,
        String accountType,
        String bankAccountNo,
        String wageEmployee,
        String isTaxable,
        String isActive,
        String isDirty,
        String encodedImage,
        String isDisable,
        String disableDate,
        String isMedical,
        String medicalDate,
        String locationID,
        String taxStateID,
        String taxStateName,
        String isPensionable,
        String changeStateID,
        String businessUnitID,
        String bvn,
        String taxIDNo,
        String taxClass,
        String bankPaymentName,
        String sfRecord,
        int nombreDePart,
        BigDecimal basicSalary,
        BigDecimal hourlyRate,
        String band,
        Long paymentInfoId,
        Long employeeLockID,
        boolean isEmployeeLocked,

        @CreatedDate
        Instant createdDate,

        @LastModifiedDate
        Instant lastModifiedDate,

        @CreatedBy
        String createdBy,

        @LastModifiedBy
        String lastModifiedBy,

        @Version
        int version
) {

    public static String formatDate(LocalDate date) {
        // Define a DateTimeFormatter with the desired format
        if(date == null){
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return date.format(formatter);
    }
}
