package com.smartleave.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A leave category (Casual, Sick, Earned, ...) with its yearly entitlement. */
@Entity
@Table(name = "leave_types")
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_name", nullable = false, unique = true, length = 50)
    private String leaveName;

    @Column(name = "annual_entitlement", nullable = false, precision = 5, scale = 1)
    private BigDecimal annualEntitlement = BigDecimal.ZERO;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    public LeaveType() {
    }

    public LeaveType(String leaveName, BigDecimal annualEntitlement, String description) {
        this.leaveName = leaveName;
        this.annualEntitlement = annualEntitlement;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLeaveName() {
        return leaveName;
    }

    public void setLeaveName(String leaveName) {
        this.leaveName = leaveName;
    }

    public BigDecimal getAnnualEntitlement() {
        return annualEntitlement;
    }

    public void setAnnualEntitlement(BigDecimal annualEntitlement) {
        this.annualEntitlement = annualEntitlement;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
