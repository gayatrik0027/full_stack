package com.example.leave.worker;

import com.example.leave.service.LeaveService;
import com.example.leave.service.LeaveService.ValidationResult;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Camunda 8 Job Workers for executing automated service tasks and user task interception.
 */
@Component
public class LeaveWorkers {

    private static final Logger log = LoggerFactory.getLogger(LeaveWorkers.class);

    private final LeaveService leaveService;

    public LeaveWorkers(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    /**
     * Executes the 'validate-leave' service task from the BPMN workflow.
     * Evaluates leave entitlement and sets workflow variables 'valid' and 'validationMessage'.
     */
    @JobWorker(type = "validate-leave", autoComplete = true)
    public Map<String, Object> handleValidateLeave(ActivatedJob job) {
        log.info("Received job 'validate-leave' [key: {}]", job.getKey());
        Map<String, Object> variables = job.getVariablesAsMap();
        Long requestId = extractRequestId(variables.get("requestId"));

        ValidationResult result = leaveService.validate(requestId);

        Map<String, Object> output = new HashMap<>();
        output.put("valid", result.isValid());
        output.put("validationMessage", result.getMessage());

        log.info("Completed job 'validate-leave' for requestId: {} with valid={}", requestId, result.isValid());
        return output;
    }

    /**
     * Captures user tasks when reached in Camunda 8, recording the job key for asynchronous completion
     * via the REST API (/api/leaves/{id}/decision).
     */
    @JobWorker(type = "io.camunda.zeebe:userTask", autoComplete = false, timeout = 2592000000L)
    public void handleUserTask(ActivatedJob job) {
        log.info("User task activated in Zeebe [jobKey: {}, elementId: {}]", job.getKey(), job.getElementId());
        Map<String, Object> variables = job.getVariablesAsMap();
        Long requestId = extractRequestId(variables.get("requestId"));

        if (requestId != null) {
            leaveService.registerPendingTask(requestId, job.getKey());
            log.info("Registered user task key {} for LeaveRequest #{}", job.getKey(), requestId);
        } else {
            log.warn("User task [jobKey: {}] had no requestId variable attached", job.getKey());
        }
    }

    /**
     * Executes the 'finalize-leave' service task from the BPMN workflow.
     * Updates final status to APPROVED or REJECTED and deducts annual leave balance if approved.
     */
    @JobWorker(type = "finalize-leave", autoComplete = true)
    public void handleFinalizeLeave(ActivatedJob job) {
        log.info("Received job 'finalize-leave' [key: {}]", job.getKey());
        Map<String, Object> variables = job.getVariablesAsMap();
        Long requestId = extractRequestId(variables.get("requestId"));

        // Outcome is configured in the BPMN ioMapping or task header
        String outcome = (String) variables.get("outcome");
        if (outcome == null || outcome.isBlank()) {
            outcome = job.getCustomHeaders().getOrDefault("outcome", "REJECTED");
        }

        String managerComment = (String) variables.get("managerComment");

        log.info("Finalizing LeaveRequest #{} with outcome: '{}', comment: '{}'", requestId, outcome, managerComment);
        leaveService.finalizeRequest(requestId, outcome, managerComment);
    }

    private Long extractRequestId(Object rawId) {
        if (rawId instanceof Number number) {
            return number.longValue();
        } else if (rawId instanceof String str) {
            return Long.parseLong(str);
        }
        return null;
    }
}
