package com.xykine.computation.model;

public enum EmployeeGroup {
    ACTIVE_MANAGER_BAND_E("active", "2A", "Manager - Band E", "NG", "N1", "N2"),
    ACTIVE_STAFF("active", "2B", "Staff", "NG", "N1", "N5"),
    ACTIVE_LABOUR("active", "2C", "labour", "NG", "N1", "N6"),
    ACTIVE_SENIOR_EXECUTIVE("active", "2D", "Senior executive", "NG", "N1", "NA"),
    ACTIVE_EX_COM("active", "2E", "Ex Com", "NG", "N1", "NG"),
    ACTIVE_SENIOR_MANAGER("active", "2F", "Senior manager", "NG", "N1", "N1"),
    ACTIVE_SUPERVISOR("active", "2G", "Supervisor", "NG", "N1", "N4"),
    ACTIVE_JUNIOR_STAFF("active", "2H", "Junior staff", "NG", "N1", "N5"),
    ACTIVE_EXPATRIATE("active", "2I", "Expatriate", "NG", "N1", "NB"),
    ACTIVE_MANAGER_BAND_F("active", "2J", "Manager - Band F", "NG", "N1", "N3"),
    RETIRED_STAFF("retired", "2K", "Staff", "NG", "N1", "N5"),
    SUSPENDED_EX_COM("suspended", "2L", "Ex Com", "NG", "N1", "NG"),
    SUSPENDED_SENIOR_MANAGER("suspended", "2M", "Senior Manger", "NG", "N1", "N1"),
    SUSPENDED_MANAGER("suspended", "2N", "manager", "NG", "N1", "N2"),
    SUSPENDED_SUPERVISOR("suspended", "2O", "Supervisor", "NG", "N1", "N4"),
    SUSPENDED_JUNIOR_STAFF("suspended", "2P", "Junior staff", "NG", "N1", "N5"),
    SUSPENDED_EXPATRIATE("suspended", "2Q", "Expatriate", "NG", "N1", "NB"),
    EXTERNAL_THIRD_PARTY("external", "2R", "Third party", "NG", "N2", "N9"),
    EXTERNAL_CONSULTANT("external", "2S", "Consultant", "NG","N2", "N7"),
    EXTERNAL_INTERN("external", "2T", "Intern", "NG", "N2", "N8"),
    EXTERNAL_FTC("external", "2U", "FTC", "NG", "N2", "N6"),
    EXTERNAL_CROSS_POSTEE("external", "2V", "Cross Postee", "NG", "N2", "NC");

    private final String group;
    private final String code;
    private final String description;
    private final String country;
    private final String payScaleType;
    private final String payScaleArea;

    EmployeeGroup(String aGroup, String aCode, String aDescription, String aCountry, String aPayScaleType, String aPayScaleArea){
        group = aGroup;
        code = aCode;
        description = aDescription;
        country = aCountry;
        payScaleType = aPayScaleType;
        payScaleArea = aPayScaleArea;
    }
}
