package com.smartleave.controller;

import com.smartleave.repository.DepartmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public read-only endpoint: the registration form needs the list of
 * departments before an account exists. Only id and name are exposed - no
 * employee counts or other internal data.
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public List<Map<String, Object>> departments() {
        return departmentRepository.findAllByOrderByDepartmentNameAsc().stream()
                .map(department -> Map.<String, Object>of(
                        "id", department.getId(),
                        "departmentName", department.getDepartmentName(),
                        "description", department.getDescription() == null ? "" : department.getDescription()))
                .toList();
    }
}
