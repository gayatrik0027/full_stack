package com.example.leave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Tracks annual leave entitlement and remaining balance for an employee.
 */
@Entity
@Table(name = "leave_balances")
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String employeeEmail;

    @Column(nullable = false)
    private int annualEntitlement = 20;

    @Column(nullable = false)
    private int remainingDays = 20;

    @Version
    private Long version;

    public LeaveBalance() {
    }

    public LeaveBalance(String employeeEmail, int annualEntitlement, int remainingDays) {
        this.employeeEmail = employeeEmail;
        this.annualEntitlement = annualEntitlement;
        this.remainingDays = remainingDays;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmployeeEmail() {
        return employeeEmail;
    }

    public void setEmployeeEmail(String employeeEmail) {
        this.employeeEmail = employeeEmail;
    }

    public int getAnnualEntitlement() {
        return annualEntitlement;
    }

    public void setAnnualEntitlement(int annualEntitlement) {
        this.annualEntitlement = annualEntitlement;
    }

    public int getRemainingDays() {
        return remainingDays;
    }

    public void setRemainingDays(int remainingDays) {
        this.remainingDays = remainingDays;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
