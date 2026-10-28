package com.example.leave.api;

import com.example.leave.domain.LeaveBalance;
import com.example.leave.domain.LeaveRequest;
import com.example.leave.service.LeaveService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller implementing the Leave Application System API contract.
 */
@RestController
@RequestMapping("/api/leaves")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class LeaveController {

    private static final Logger log = LoggerFactory.getLogger(LeaveController.class);

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    /**
     * POST /api/leaves
     * Submit a leave application.
     * Returns HTTP 201 Created with the saved leave entity.
     */
    @PostMapping
    public ResponseEntity<LeaveRequest> submitLeave(@Valid @RequestBody SubmitLeaveRequest request) {
        log.info("Received submit leave request for {}", request.getEmployeeEmail());
        LeaveRequest created = leaveService.submit(request);
        URI location = URI.create("/api/leaves/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }

    /**
     * GET /api/leaves?employeeEmail=alice@example.com
     * Return all leave applications submitted by a specific employee.
     */
    @GetMapping
    public ResponseEntity<List<LeaveRequest>> getLeaves(@RequestParam(required = false) String employeeEmail) {
        if (employeeEmail == null || employeeEmail.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<LeaveRequest> requests = leaveService.getRequestsByEmployee(employeeEmail.trim());
        return ResponseEntity.ok(requests);
    }

    /**
     * GET /api/leaves/pending
     * Return all leave applications currently awaiting manager review and approval.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<LeaveRequest>> getPendingLeaves() {
        List<LeaveRequest> pending = leaveService.getPendingRequests();
        return ResponseEntity.ok(pending);
    }

    /**
     * POST /api/leaves/{id}/decision
     * Accepts manager decision JSON { "approved": true/false, "comment": "..." }.
     * Completes the corresponding Camunda user task.
     * Returns HTTP 204 No Content on success.
     * Returns HTTP 409 Conflict if not awaiting approval or the task is not yet available.
     */
    @PostMapping("/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> submitDecision(@PathVariable Long id,
                                               @Valid @RequestBody DecisionRequest decision) {
        log.info("Received decision for LeaveRequest #{}: approved={}", id, decision.getApproved());
        leaveService.decide(id, decision);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/leaves/balance?employeeEmail=alice@example.com
     * Return employee leave balance including remaining annual leave days.
     */
    @GetMapping("/balance")
    public ResponseEntity<LeaveBalance> getBalance(@RequestParam String employeeEmail) {
        if (employeeEmail == null || employeeEmail.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        LeaveBalance balance = leaveService.balance(employeeEmail.trim());
        return ResponseEntity.ok(balance);
    }
}
