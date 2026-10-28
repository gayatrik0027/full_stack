package com.example.leave;

import com.example.leave.api.ApiExceptionHandler.TaskNotAvailableException;
import com.example.leave.api.DecisionRequest;
import com.example.leave.api.LeaveController;
import com.example.leave.api.SubmitLeaveRequest;
import com.example.leave.domain.LeaveBalance;
import com.example.leave.domain.LeaveRequest;
import com.example.leave.domain.LeaveStatus;
import com.example.leave.domain.LeaveType;
import com.example.leave.service.LeaveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller integration tests verifying REST API contracts and HTTP status codes.
 */
@WebMvcTest(LeaveController.class)
class LeaveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LeaveService leaveService;

    @Test
    @DisplayName("POST /api/leaves returns HTTP 201 on valid submission")
    void testSubmitLeave_Success() throws Exception {
        SubmitLeaveRequest request = new SubmitLeaveRequest(
            "Alice Smith",
            "alice@example.com",
            LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 6),
            "Family trip"
        );

        LeaveRequest created = new LeaveRequest(
            "Alice Smith", "alice@example.com", LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Family trip"
        );
        created.setId(1L);

        when(leaveService.submit(any(SubmitLeaveRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/leaves/1"))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.employeeEmail").value("alice@example.com"))
            .andExpect(jsonPath("$.workingDays").value(5))
            .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("POST /api/leaves returns HTTP 400 when email is invalid")
    void testSubmitLeave_InvalidEmail_Returns400() throws Exception {
        SubmitLeaveRequest request = new SubmitLeaveRequest(
            "Alice Smith",
            "not-an-email", // invalid
            LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 6),
            "Family trip"
        );

        mockMvc.perform(post("/api/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.employeeEmail").exists());
    }

    @Test
    @DisplayName("GET /api/leaves?employeeEmail=... returns employee requests")
    void testGetLeaves_ReturnsList() throws Exception {
        LeaveRequest req = new LeaveRequest(
            "Alice Smith", "alice@example.com", LeaveType.ANNUAL,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 5, "Vacation"
        );
        req.setId(10L);

        when(leaveService.getRequestsByEmployee("alice@example.com")).thenReturn(List.of(req));

        mockMvc.perform(get("/api/leaves")
                .param("employeeEmail", "alice@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].employeeEmail").value("alice@example.com"));
    }

    @Test
    @DisplayName("GET /api/leaves/pending returns pending manager approval requests")
    void testGetPendingLeaves_ReturnsList() throws Exception {
        LeaveRequest req = new LeaveRequest(
            "Bob Jones", "bob@example.com", LeaveType.SICK,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), 3, "Flu"
        );
        req.setId(20L);
        req.setStatus(LeaveStatus.PENDING_APPROVAL);

        when(leaveService.getPendingRequests()).thenReturn(List.of(req));

        mockMvc.perform(get("/api/leaves/pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(20))
            .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("POST /api/leaves/{id}/decision returns HTTP 204 on success")
    void testSubmitDecision_Success() throws Exception {
        DecisionRequest decision = new DecisionRequest(true, "Approved by team lead");

        doNothing().when(leaveService).decide(eq(10L), any(DecisionRequest.class));

        mockMvc.perform(post("/api/leaves/10/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(decision)))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/leaves/{id}/decision returns HTTP 409 when task is not yet available")
    void testSubmitDecision_TaskNotAvailable_Returns409() throws Exception {
        DecisionRequest decision = new DecisionRequest(true, "Approved");

        doThrow(new TaskNotAvailableException("Manager approval task is not yet available"))
            .when(leaveService).decide(eq(10L), any(DecisionRequest.class));

        mockMvc.perform(post("/api/leaves/10/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(decision)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message").value("Manager approval task is not yet available"));
    }

    @Test
    @DisplayName("GET /api/leaves/balance returns employee leave balance")
    void testGetBalance_ReturnsBalance() throws Exception {
        LeaveBalance balance = new LeaveBalance("alice@example.com", 20, 15);

        when(leaveService.balance("alice@example.com")).thenReturn(balance);

        mockMvc.perform(get("/api/leaves/balance")
                .param("employeeEmail", "alice@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.employeeEmail").value("alice@example.com"))
            .andExpect(jsonPath("$.annualEntitlement").value(20))
            .andExpect(jsonPath("$.remainingDays").value(15));
    }
}
