package com.smartleave.repository;

import com.smartleave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findByActiveTrueOrderByLeaveNameAsc();

    Optional<LeaveType> findByLeaveNameIgnoreCase(String leaveName);
}
