package com.smartleave.repository;

import com.smartleave.entity.LeaveApplication;
import com.smartleave.entity.LeaveStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveApplicationRepository
        extends JpaRepository<LeaveApplication, Long>, JpaSpecificationExecutor<LeaveApplication> {

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, LeaveStatus status);

    long countByStatus(LeaveStatus status);

    long countByUser_Manager_Id(Long managerId);

    long countByUser_Manager_IdAndStatus(Long managerId, LeaveStatus status);

    long countByUser_IdAndStatus(Long userId, LeaveStatus status);

    @Query("""
            select a from LeaveApplication a
              join fetch a.leaveType
              join fetch a.user u
              join fetch u.department
             where u.manager.id = :managerId
             order by a.appliedAt desc
            """)
    List<LeaveApplication> findByManager(@Param("managerId") Long managerId);

    @Query("""
            select a from LeaveApplication a
              join fetch a.leaveType
              join fetch a.user u
              join fetch u.department
             where u.id = :userId
             order by a.appliedAt desc
            """)
    List<LeaveApplication> findByUser(@Param("userId") Long userId);

    /**
     * Rows that overlap the given range and are still "active" for the
     * overlap rule (PENDING or APPROVED). Used to prevent double booking.
     */
    @Query("""
            select a from LeaveApplication a
             where a.user.id = :userId
               and a.status in (com.smartleave.entity.LeaveStatus.PENDING,
                                com.smartleave.entity.LeaveStatus.APPROVED)
               and a.startDate <= :endDate
               and a.endDate >= :startDate
               and (:excludeId is null or a.id <> :excludeId)
            """)
    List<LeaveApplication> findOverlapping(@Param("userId") Long userId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate,
                                           @Param("excludeId") Long excludeId);

    /** Locks the request row so a repeated approve/reject cannot double-deduct. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LeaveApplication a where a.id = :id")
    Optional<LeaveApplication> findForUpdate(@Param("id") Long id);

    Optional<LeaveApplication> findByIdAndUser_Id(Long id, Long userId);

    // ------------------------------------------------------------ reporting
    @Query("""
            select u.department.id, count(a)
              from LeaveApplication a join a.user u
             group by u.department.id
            """)
    List<Object[]> applicationCountPerDepartment();

    @Query("""
            select u.department.id, count(a), sum(a.totalDays)
              from LeaveApplication a join a.user u
             where a.status = com.smartleave.entity.LeaveStatus.APPROVED
             group by u.department.id
            """)
    List<Object[]> approvedStatsPerDepartment();

    @Query("""
            select year(a.appliedAt), month(a.appliedAt), count(a)
              from LeaveApplication a
             group by year(a.appliedAt), month(a.appliedAt)
            """)
    List<Object[]> applicationCountPerMonth();

    @Query("""
            select year(a.appliedAt), month(a.appliedAt), count(a), sum(a.totalDays)
              from LeaveApplication a
             where a.status = com.smartleave.entity.LeaveStatus.APPROVED
             group by year(a.appliedAt), month(a.appliedAt)
            """)
    List<Object[]> approvedStatsPerMonth();

    @Query("""
            select count(a) from LeaveApplication a
             where a.user.manager.id = :managerId
               and a.status = com.smartleave.entity.LeaveStatus.APPROVED
               and a.startDate <= :today
               and a.endDate >= :today
            """)
    long countTeamOnLeave(@Param("managerId") Long managerId, @Param("today") java.time.LocalDate today);

    @Query("""
            select a.user.id, a.status, count(a)
              from LeaveApplication a
             where a.user.manager.id = :managerId
             group by a.user.id, a.status
            """)
    List<Object[]> teamStatusCounts(@Param("managerId") Long managerId);
}
