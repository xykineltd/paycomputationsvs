// seedPayrollConfig.js

// change "aced_payroll" to your actual database name
use("staging");

// ---------- TAX RULES (collection: taxt) ----------

// Drop existing records so we don't duplicate
db.tax.deleteMany({});

const oldTaxRule = `[
  {"limit": 300000, "rate": 7},
  {"limit": 300000, "rate": 11},
  {"limit": 500000, "rate": 15},
  {"limit": 500000, "rate": 19},
  {"limit": 1600000, "rate": 21},
  {"limit": null, "rate": 24}
]`;

const newTaxRule = `[
  { "limit": 800000,    "rate": 0 },
  { "limit": 3000000,   "rate": 15 },
  { "limit": 12000000,  "rate": 18 },
  { "limit": 25000000,  "rate": 21 },
  { "limit": 50000000,  "rate": 23 },
  { "limit": null,      "rate": 25 }
]`;

db.tax.insertMany([
    {
        country: "NIGERIA",
        taxRule: oldTaxRule,
        active: true
    },
    {
        country: "NIGERIA",
        taxRule: newTaxRule,
        active: false
    }
]);

// ---------- COMPUTATION CONSTANTS (collection: computationConstants) ----------

db.computationConstants.deleteMany({});

db.computationConstants.insertMany([
    {
        _id: "pensionFundPercent",
        description: "The percentage of basic salary and other relevant allowances that goes into employee´s pension",
        value: NumberDecimal("0.08")
    },
    {
        _id: "employerPensionContributionPercent",
        description: "Employer pension contribution percentage",
        value: NumberDecimal("0.10")
    },
    {
        _id: "nationalHousingFundPercent",
        description: "The percentage of basic salary for national housing fund",
        value: NumberDecimal("0.025")
    },
    {
        _id: "craFraction",
        description: "Used to calculate fixed consolidated tax relief",
        value: NumberDecimal("0.01")
    },
    {
        _id: "variableCRAFraction",
        description: "Used to calculate variable consolidated tax relief",
        value: NumberDecimal("0.20")
    },
    {
        _id: "craCutOff",
        description: "CRA cut off",
        value: NumberDecimal("200000")
    },
    {
        _id: "withHoldingTax",
        description: "WithHolding tax",
        value: NumberDecimal("0.05")
    }
]);

print("✅ Seed complete: taxt + computationConstants");
