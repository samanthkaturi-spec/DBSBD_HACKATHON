package com.smartleave.repository;

import com.smartleave.entity.Role;
import com.smartleave.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByEmployeeIdIgnoreCase(String employeeId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmployeeIdIgnoreCase(String employeeId);

    List<User> findByManagerIdOrderByFullNameAsc(Long managerId);

    List<User> findByDepartmentIdOrderByFullNameAsc(Long departmentId);

    List<User> findByRoleOrderByFullNameAsc(Role role);

    long countByRole(Role role);

    long countByManagerId(Long managerId);

    @Query("select u from User u join fetch u.department where u.email = :email")
    Optional<User> findWithDepartmentByEmail(@Param("email") String email);

    @Query("select u from User u join fetch u.department where u.id = :id")
    Optional<User> findWithDepartmentById(@Param("id") Long id);

    @Query("""
            select u from User u join fetch u.department
             where (:q is null or lower(u.fullName) like lower(concat('%', :q, '%'))
                     or lower(u.email) like lower(concat('%', :q, '%'))
                     or lower(u.employeeId) like lower(concat('%', :q, '%')))
               and (:departmentId is null or u.department.id = :departmentId)
             order by u.fullName asc
            """)
    List<User> search(@Param("q") String q, @Param("departmentId") Long departmentId);

    @Query("select u.department.id, count(u) from User u group by u.department.id")
    List<Object[]> employeeCountsPerDepartment();
}
