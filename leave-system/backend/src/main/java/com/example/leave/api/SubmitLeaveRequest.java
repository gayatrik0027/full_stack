package com.example.leave.api;

import com.example.leave.domain.LeaveType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload for submitting a new leave application.
 */
public class SubmitLeaveRequest {

    @NotBlank(message = "Employee name is required")
    @Size(max = 120, message = "Employee name must not exceed 120 characters")
    private String employeeName;

    @NotBlank(message = "Employee email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 120, message = "Employee email must not exceed 120 characters")
    private String employeeEmail;

    @NotNull(message = "Leave type is required")
    private LeaveType leaveType;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;

    public SubmitLeaveRequest() {
    }

    public SubmitLeaveRequest(String employeeName, String employeeEmail, LeaveType leaveType,
                              LocalDate startDate, LocalDate endDate, String reason) {
        this.employeeName = employeeName;
        this.employeeEmail = employeeEmail;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeEmail() {
        return employeeEmail;
    }

    public void setEmployeeEmail(String employeeEmail) {
        this.employeeEmail = employeeEmail;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
