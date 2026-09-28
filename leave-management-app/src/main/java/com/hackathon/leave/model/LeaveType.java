package com.hackathon.leave.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "leave_type")
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ANNUAL, SICK, CASUAL. */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private int annualQuotaDays;

    public LeaveType() {
    }

    public LeaveType(String code, int annualQuotaDays) {
        this.code = code;
        this.annualQuotaDays = annualQuotaDays;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getAnnualQuotaDays() {
        return annualQuotaDays;
    }

    public void setAnnualQuotaDays(int annualQuotaDays) {
        this.annualQuotaDays = annualQuotaDays;
    }
}
