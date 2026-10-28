package com.example.leave.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for LeaveRequest.
 */
@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeEmailOrderByCreatedAtDesc(String employeeEmail);

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveStatus status);

    Optional<LeaveRequest> findByProcessInstanceKey(Long processInstanceKey);
}
