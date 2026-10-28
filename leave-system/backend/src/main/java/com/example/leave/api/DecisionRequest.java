package com.example.leave.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for manager approval / rejection decisions.
 */
public class DecisionRequest {

    @NotNull(message = "Approval decision (true/false) is required")
    private Boolean approved;

    @Size(max = 500, message = "Comment must not exceed 500 characters")
    private String comment;

    public DecisionRequest() {
    }

    public DecisionRequest(Boolean approved, String comment) {
        this.approved = approved;
        this.comment = comment;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
