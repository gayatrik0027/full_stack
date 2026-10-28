package com.example.leave.service;

import com.example.leave.api.ApiExceptionHandler.ResourceNotFoundException;
import com.example.leave.api.ApiExceptionHandler.TaskNotAvailableException;
import com.example.leave.api.DecisionRequest;
import com.example.leave.api.SubmitLeaveRequest;
import com.example.leave.domain.LeaveBalance;
import com.example.leave.domain.LeaveBalanceRepository;
import com.example.leave.domain.LeaveRequest;
import com.example.leave.domain.LeaveRequestRepository;
import com.example.leave.domain.LeaveStatus;
import com.example.leave.domain.LeaveType;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core business service for leave applications, balance tracking, and Camunda workflow integration.
 */
@Service
public class LeaveService {

    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final ZeebeClient zeebeClient;

    public LeaveService(LeaveRequestRepository leaveRequestRepository,
                        LeaveBalanceRepository leaveBalanceRepository,
                        @Autowired(required = false) ZeebeClient zeebeClient) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.zeebeClient = zeebeClient;
    }

    /**
     * Calculates the number of working days between two dates, excluding Saturdays and Sundays.
     */
    public static int calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date must not be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        int workingDays = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            DayOfWeek day = current.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }

    /**
     * Submits a new leave application, validates input parameters, creates the database record,
     * and triggers the Camunda 8 BPMN process.
     */
    @Transactional
    public LeaveRequest submit(SubmitLeaveRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }

        int workingDays = calculateWorkingDays(request.getStartDate(), request.getEndDate());
        if (workingDays <= 0) {
            throw new IllegalArgumentException("Leave request must contain at least 1 working day (excluding weekends)");
        }

        // Ensure employee has a balance record (default 20 days entitlement)
        balance(request.getEmployeeEmail());

        LeaveRequest leaveRequest = new LeaveRequest(
            request.getEmployeeName(),
            request.getEmployeeEmail(),
            request.getLeaveType(),
            request.getStartDate(),
            request.getEndDate(),
            workingDays,
            request.getReason()
        );

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        log.info("Saved LeaveRequest #{} for employee {}", saved.getId(), saved.getEmployeeEmail());

        // Start Camunda Zeebe workflow instance
        if (zeebeClient != null) {
            try {
                Map<String, Object> variables = new HashMap<>();
                variables.put("requestId", saved.getId());
                variables.put("employeeEmail", saved.getEmployeeEmail());
                variables.put("leaveType", saved.getLeaveType().name());
                variables.put("days", saved.getWorkingDays());
                variables.put("startDate", saved.getStartDate().toString());
                variables.put("endDate", saved.getEndDate().toString());

                ProcessInstanceEvent event = zeebeClient.newCreateInstanceCommand()
                    .bpmnProcessId("leave-request")
                    .latestVersion()
                    .variables(variables)
                    .send()
                    .join();

                saved.setProcessInstanceKey(event.getProcessInstanceKey());
                saved = leaveRequestRepository.save(saved);
                log.info("Started Camunda process instance {} for LeaveRequest #{}",
                    event.getProcessInstanceKey(), saved.getId());
            } catch (Exception e) {
                log.warn("Unable to start Camunda workflow for LeaveRequest #{}: {}. Running in local offline mode.",
                    saved.getId(), e.getMessage());
            }
        } else {
            log.warn("ZeebeClient is not configured. LeaveRequest #{} saved without workflow execution.", saved.getId());
        }

        return saved;
    }

    /**
     * Validates a leave request (called by validate-leave job worker).
     * Checks annual leave balance and updates status accordingly.
     */
    @Transactional
    public ValidationResult validate(Long requestId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id: " + requestId));

        boolean valid;
        String message;

        if (leaveRequest.getLeaveType() == LeaveType.ANNUAL) {
            LeaveBalance balance = balance(leaveRequest.getEmployeeEmail());
            if (balance.getRemainingDays() < leaveRequest.getWorkingDays()) {
                valid = false;
                message = "Insufficient leave balance: requested " + leaveRequest.getWorkingDays()
                    + " days, but remaining balance is " + balance.getRemainingDays() + " days.";
                leaveRequest.setStatus(LeaveStatus.REJECTED);
                leaveRequest.setStatusMessage(message);
            } else {
                valid = true;
                message = "Validation passed, awaiting manager approval.";
                leaveRequest.setStatus(LeaveStatus.PENDING_APPROVAL);
                leaveRequest.setStatusMessage(message);
            }
        } else {
            // SICK and UNPAID leave requests do not require annual leave balance check
            valid = true;
            message = "Validation passed (" + leaveRequest.getLeaveType() + " leave), awaiting manager approval.";
            leaveRequest.setStatus(LeaveStatus.PENDING_APPROVAL);
            leaveRequest.setStatusMessage(message);
        }

        leaveRequestRepository.save(leaveRequest);
        log.info("Validation completed for LeaveRequest #{}: valid={}, message='{}'", requestId, valid, message);
        return new ValidationResult(valid, message);
    }

    /**
     * Registers active user task key received from Camunda when manager approval task is activated.
     */
    @Transactional
    public void registerPendingTask(Long requestId, Long taskKey) {
        leaveRequestRepository.findById(requestId).ifPresent(leaveRequest -> {
            leaveRequest.setTaskKey(taskKey);
            leaveRequest.setStatus(LeaveStatus.PENDING_APPROVAL);
            leaveRequestRepository.save(leaveRequest);
            log.info("Registered user task key {} for LeaveRequest #{}", taskKey, requestId);
        });
    }

    /**
     * Handles manager decision (Approve or Reject), completing the Camunda user task.
     */
    @Transactional
    public void decide(Long id, DecisionRequest decision) {
        LeaveRequest leave = leaveRequestRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id: " + id));

        if (leave.getStatus() != LeaveStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Leave request #" + id + " is not awaiting manager approval (current status: "
                + leave.getStatus() + ")");
        }

        if (leave.getTaskKey() == null) {
            throw new TaskNotAvailableException("Manager approval task is not yet available in Camunda for request #" + id);
        }

        if (zeebeClient != null) {
            try {
                Map<String, Object> variables = new HashMap<>();
                variables.put("approved", decision.getApproved());
                variables.put("managerComment", decision.getComment() != null ? decision.getComment() : "");

                zeebeClient.newCompleteCommand(leave.getTaskKey())
                    .variables(variables)
                    .send()
                    .join();

                log.info("Completed Camunda user task {} for LeaveRequest #{} with approved={}",
                    leave.getTaskKey(), id, decision.getApproved());

                leave.setTaskKey(null);
                if (decision.getComment() != null) {
                    leave.setManagerComment(decision.getComment());
                }
                leaveRequestRepository.save(leave);
            } catch (Exception e) {
                log.error("Failed to complete Camunda task {}: {}", leave.getTaskKey(), e.getMessage());
                throw new RuntimeException("Failed to complete Camunda task: " + e.getMessage(), e);
            }
        } else {
            // Local fallback if Camunda client is absent
            String outcome = Boolean.TRUE.equals(decision.getApproved()) ? "APPROVED" : "REJECTED";
            finalizeRequest(id, outcome, decision.getComment());
        }
    }

    /**
     * Finalizes the leave request outcome (called by finalize-leave job worker).
     * Prevents duplicate balance deductions if retried.
     */
    @Transactional
    public LeaveRequest finalizeRequest(Long requestId, String outcome, String managerComment) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id: " + requestId));

        // Idempotency check: if already finalized with same outcome, do not re-execute balance deduction
        if ("APPROVED".equalsIgnoreCase(outcome) && leaveRequest.getStatus() == LeaveStatus.APPROVED) {
            log.info("LeaveRequest #{} is already marked APPROVED. Skipping duplicate finalization.", requestId);
            return leaveRequest;
        }
        if ("REJECTED".equalsIgnoreCase(outcome) && leaveRequest.getStatus() == LeaveStatus.REJECTED) {
            log.info("LeaveRequest #{} is already marked REJECTED. Skipping duplicate finalization.", requestId);
            return leaveRequest;
        }

        if (managerComment != null && !managerComment.isBlank()) {
            leaveRequest.setManagerComment(managerComment);
        }

        if ("APPROVED".equalsIgnoreCase(outcome)) {
            leaveRequest.setStatus(LeaveStatus.APPROVED);
            leaveRequest.setStatusMessage("Approved by manager");

            // Deduct annual leave balance only once
            if (leaveRequest.getLeaveType() == LeaveType.ANNUAL && !leaveRequest.isBalanceDeducted()) {
                LeaveBalance balance = balance(leaveRequest.getEmployeeEmail());
                int updatedRemaining = balance.getRemainingDays() - leaveRequest.getWorkingDays();
                balance.setRemainingDays(Math.max(0, updatedRemaining));
                leaveBalanceRepository.save(balance);
                leaveRequest.setBalanceDeducted(true);
                log.info("Deducted {} working days for employee {}. New remaining balance: {}",
                    leaveRequest.getWorkingDays(), leaveRequest.getEmployeeEmail(), balance.getRemainingDays());
            }
        } else {
            leaveRequest.setStatus(LeaveStatus.REJECTED);
            if (leaveRequest.getStatusMessage() == null || !leaveRequest.getStatusMessage().contains("Insufficient")) {
                leaveRequest.setStatusMessage("Rejected by manager");
            }
            log.info("LeaveRequest #{} marked as REJECTED", requestId);
        }

        return leaveRequestRepository.save(leaveRequest);
    }

    /**
     * Gets or initializes the leave balance for an employee (default 20 days entitlement).
     */
    @Transactional
    public LeaveBalance balance(String employeeEmail) {
        return leaveBalanceRepository.findByEmployeeEmail(employeeEmail)
            .orElseGet(() -> {
                LeaveBalance initialBalance = new LeaveBalance(employeeEmail, 20, 20);
                log.info("Created initial leave balance (20 days) for new employee: {}", employeeEmail);
                return leaveBalanceRepository.save(initialBalance);
            });
    }

    /**
     * Returns all leave requests for a given employee ordered by creation date descending.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequest> getRequestsByEmployee(String employeeEmail) {
        return leaveRequestRepository.findByEmployeeEmailOrderByCreatedAtDesc(employeeEmail);
    }

    /**
     * Returns all leave requests currently awaiting manager approval.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequest> getPendingRequests() {
        return leaveRequestRepository.findByStatusOrderByCreatedAtDesc(LeaveStatus.PENDING_APPROVAL);
    }

    /**
     * Finds a single leave request by ID.
     */
    @Transactional(readOnly = true)
    public LeaveRequest getRequestById(Long id) {
        return leaveRequestRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id: " + id));
    }

    /**
     * Helper record for validation result.
     */
    public record ValidationResult(boolean valid, String message) {
        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
