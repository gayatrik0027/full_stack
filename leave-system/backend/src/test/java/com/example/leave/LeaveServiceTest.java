package com.example.leave;

import com.example.leave.api.ApiExceptionHandler.TaskNotAvailableException;
import com.example.leave.api.DecisionRequest;
import com.example.leave.api.SubmitLeaveRequest;
import com.example.leave.domain.LeaveBalance;
import com.example.leave.domain.LeaveBalanceRepository;
import com.example.leave.domain.LeaveRequest;
import com.example.leave.domain.LeaveRequestRepository;
import com.example.leave.domain.LeaveStatus;
import com.example.leave.domain.LeaveType;
import com.example.leave.service.LeaveService;
import com.example.leave.service.LeaveService.ValidationResult;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for LeaveService business logic.
 * Tests working-day calculation, date validation, insufficient balance, idempotency, and task conflict handling.
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private ZeebeClient zeebeClient;

    private LeaveService leaveService;

    @BeforeEach
    void setUp() {
        leaveService = new LeaveService(leaveRequestRepository, leaveBalanceRepository, zeebeClient);
    }

    // 1. Working Day Calculation Tests

    @Test
    @DisplayName("Working days calculation across a regular Monday-Friday week returns 5 days")
    void testCalculateWorkingDays_StandardWeek() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        LocalDate friday = LocalDate.of(2026, 11, 6);
        int days = LeaveService.calculateWorkingDays(monday, friday);
        assertEquals(5, days);
    }

    @Test
    @DisplayName("Working days calculation across a weekend skips Saturday and Sunday")
    void testCalculateWorkingDays_AcrossWeekend() {
        // Friday to Tuesday = Friday, Monday, Tuesday (3 working days)
        LocalDate friday = LocalDate.of(2026, 11, 6);
        LocalDate tuesday = LocalDate.of(2026, 11, 10);
        int days = LeaveService.calculateWorkingDays(friday, tuesday);
        assertEquals(3, days);
    }

    @Test
    @DisplayName("Working days calculation over pure weekend returns 0 days")
    void testCalculateWorkingDays_WeekendOnly() {
        LocalDate saturday = LocalDate.of(2026, 11, 7);
        LocalDate sunday = LocalDate.of(2026, 11, 8);
        int days = LeaveService.calculateWorkingDays(saturday, sunday);
        assertEquals(0, days);
    }

    @Test
    @DisplayName("End date before start date throws IllegalArgumentException")
    void testCalculateWorkingDays_EndDateBeforeStartDate() {
        LocalDate start = LocalDate.of(2026, 11, 10);
        LocalDate end = LocalDate.of(2026, 11, 5);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> LeaveService.calculateWorkingDays(start, end));
        assertTrue(ex.getMessage().contains("End date cannot be before start date"));
    }

    // 2. Submit Validation Tests

    @Test
    @DisplayName("Submitting a request that falls solely on weekend throws IllegalArgumentException")
    void testSubmit_ZeroWorkingDays_ThrowsException() {
        SubmitLeaveRequest req = new SubmitLeaveRequest(
            "Alice Smith",
            "alice@example.com",
            LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 7), // Saturday
            LocalDate.of(2026, 11, 8), // Sunday
            "Weekend getaway"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> leaveService.submit(req));
        assertTrue(ex.getMessage().contains("at least 1 working day"));
    }

    // 3. Workflow Validation Tests (validate-leave worker)

    @Test
    @DisplayName("Annual leave with sufficient balance passes validation and sets PENDING_APPROVAL")
    void testValidate_SufficientBalance_Passes() {
        LeaveRequest request = new LeaveRequest(
            "Alice Smith", "alice@example.com", LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Vacation"
        );
        request.setId(101L);

        LeaveBalance balance = new LeaveBalance("alice@example.com", 20, 20);

        when(leaveRequestRepository.findById(101L)).thenReturn(Optional.of(request));
        when(leaveBalanceRepository.findByEmployeeEmail("alice@example.com")).thenReturn(Optional.of(balance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ValidationResult result = leaveService.validate(101L);

        assertTrue(result.isValid());
        assertEquals(LeaveStatus.PENDING_APPROVAL, request.getStatus());
        assertTrue(result.getMessage().contains("Validation passed"));
    }

    @Test
    @DisplayName("Annual leave with insufficient balance fails validation and marks REJECTED")
    void testValidate_InsufficientBalance_Fails() {
        LeaveRequest request = new LeaveRequest(
            "Bob Jones", "bob@example.com", LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 13), 10, "Long trip"
        );
        request.setId(102L);

        // Bob only has 5 days remaining
        LeaveBalance balance = new LeaveBalance("bob@example.com", 20, 5);

        when(leaveRequestRepository.findById(102L)).thenReturn(Optional.of(request));
        when(leaveBalanceRepository.findByEmployeeEmail("bob@example.com")).thenReturn(Optional.of(balance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ValidationResult result = leaveService.validate(102L);

        assertFalse(result.isValid());
        assertEquals(LeaveStatus.REJECTED, request.getStatus());
        assertTrue(result.getMessage().contains("Insufficient leave balance"));
    }

    @Test
    @DisplayName("Sick leave does not check annual balance and passes validation")
    void testValidate_SickLeave_PassesWithoutDeductingBalance() {
        LeaveRequest request = new LeaveRequest(
            "Charlie Brown", "charlie@example.com", LeaveType.SICK,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Flu"
        );
        request.setId(103L);

        when(leaveRequestRepository.findById(103L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ValidationResult result = leaveService.validate(103L);

        assertTrue(result.isValid());
        assertEquals(LeaveStatus.PENDING_APPROVAL, request.getStatus());
        verify(leaveBalanceRepository, never()).findByEmployeeEmail(anyString());
    }

    // 4. Decision & Task Conflict Tests

    @Test
    @DisplayName("Deciding a request when taskKey is null throws TaskNotAvailableException (HTTP 409)")
    void testDecide_TaskKeyNull_ThrowsConflict() {
        LeaveRequest request = new LeaveRequest(
            "Alice Smith", "alice@example.com", LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Vacation"
        );
        request.setId(104L);
        request.setStatus(LeaveStatus.PENDING_APPROVAL);
        request.setTaskKey(null); // Task not yet reached in Zeebe

        when(leaveRequestRepository.findById(104L)).thenReturn(Optional.of(request));

        DecisionRequest decision = new DecisionRequest(true, "Looks good");

        assertThrows(TaskNotAvailableException.class, () -> leaveService.decide(104L, decision));
    }

    @Test
    @DisplayName("Deciding a request not in PENDING_APPROVAL status throws IllegalStateException (HTTP 409)")
    void testDecide_WrongStatus_ThrowsConflict() {
        LeaveRequest request = new LeaveRequest(
            "Alice Smith", "alice@example.com", LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Vacation"
        );
        request.setId(105L);
        request.setStatus(LeaveStatus.SUBMITTED);

        when(leaveRequestRepository.findById(105L)).thenReturn(Optional.of(request));

        DecisionRequest decision = new DecisionRequest(true, "Looks good");

        assertThrows(IllegalStateException.class, () -> leaveService.decide(105L, decision));
    }

    // 5. Finalization and Idempotency Tests

    @Test
    @DisplayName("Finalizing approved annual leave deducts balance and sets balanceDeducted flag")
    void testFinalize_Approved_DeductsBalanceOnce() {
        LeaveRequest request = new LeaveRequest(
            "Alice Smith", "alice@example.com", LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Vacation"
        );
        request.setId(106L);
        request.setStatus(LeaveStatus.PENDING_APPROVAL);

        LeaveBalance balance = new LeaveBalance("alice@example.com", 20, 20);

        when(leaveRequestRepository.findById(106L)).thenReturn(Optional.of(request));
        when(leaveBalanceRepository.findByEmployeeEmail("alice@example.com")).thenReturn(Optional.of(balance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequest finalized = leaveService.finalizeRequest(106L, "APPROVED", "Enjoy your vacation");

        assertEquals(LeaveStatus.APPROVED, finalized.getStatus());
        assertTrue(finalized.isBalanceDeducted());
        assertEquals(15, balance.getRemainingDays());
        verify(leaveBalanceRepository, times(1)).save(balance);
    }

    @Test
    @DisplayName("Idempotency: Retrying finalize on already APPROVED request does not deduct balance again")
    void testFinalize_Idempotent_RetriedJobDoesNotDeductTwice() {
        LeaveRequest request = new LeaveRequest(
            "Alice Smith", "alice@example.com", LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Vacation"
        );
        request.setId(107L);
        request.setStatus(LeaveStatus.APPROVED); // Already approved!
        request.setBalanceDeducted(true);

        when(leaveRequestRepository.findById(107L)).thenReturn(Optional.of(request));

        LeaveRequest result = leaveService.finalizeRequest(107L, "APPROVED", "Retry comment");

        assertEquals(LeaveStatus.APPROVED, result.getStatus());
        // Verify balance repository was NEVER called during the retry
        verify(leaveBalanceRepository, never()).findByEmployeeEmail(anyString());
        verify(leaveBalanceRepository, never()).save(any(LeaveBalance.class));
    }

    @Test
    @DisplayName("Finalizing sick leave does not deduct annual leave balance")
    void testFinalize_SickLeave_NoBalanceDeduction() {
        LeaveRequest request = new LeaveRequest(
            "Charlie Brown", "charlie@example.com", LeaveType.SICK,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Medical leave"
        );
        request.setId(108L);
        request.setStatus(LeaveStatus.PENDING_APPROVAL);

        when(leaveRequestRepository.findById(108L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequest finalized = leaveService.finalizeRequest(108L, "APPROVED", "Get well soon");

        assertEquals(LeaveStatus.APPROVED, finalized.getStatus());
        assertFalse(finalized.isBalanceDeducted());
        verify(leaveBalanceRepository, never()).findByEmployeeEmail(anyString());
    }
}
