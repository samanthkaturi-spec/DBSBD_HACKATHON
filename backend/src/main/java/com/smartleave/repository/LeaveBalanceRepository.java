package com.smartleave.repository;

import com.smartleave.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    @Query("""
            select b from LeaveBalance b
              join fetch b.leaveType
             where b.user.id = :userId and b.year = :year
             order by b.leaveType.leaveName
            """)
    List<LeaveBalance> findByUserAndYear(@Param("userId") Long userId, @Param("year") Integer year);

    /**
     * Pessimistic row lock: approval and cancellation run inside a transaction
     * and this guarantees only one transaction can mutate a given balance at a
     * time, which prevents lost updates under concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from LeaveBalance b
             where b.user.id = :userId
               and b.leaveType.id = :leaveTypeId
               and b.year = :year
            """)
    Optional<LeaveBalance> findForUpdate(@Param("userId") Long userId,
                                         @Param("leaveTypeId") Long leaveTypeId,
                                         @Param("year") Integer year);

    Optional<LeaveBalance> findByUserIdAndLeaveType_IdAndYear(Long userId, Long leaveTypeId, Integer year);
}
