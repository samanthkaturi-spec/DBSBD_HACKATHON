package com.smartleave.service;

import com.smartleave.entity.LeaveBalance;
import com.smartleave.entity.LeaveType;
import com.smartleave.entity.User;
import com.smartleave.exception.ApiException;
import com.smartleave.repository.LeaveBalanceRepository;
import com.smartleave.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every balance mutation happens here, inside a transaction, on a row locked
 * with {@code SELECT ... FOR UPDATE}. That makes concurrent approvals safe:
 * two managers can never deduct the same days twice.
 */
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveBalanceService(LeaveBalanceRepository balanceRepository,
                               LeaveTypeRepository leaveTypeRepository) {
        this.balanceRepository = balanceRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    /** Creates any missing (user, type, year) rows from the yearly entitlement. Idempotent. */
    @Transactional
    public void ensureYearBalances(User user, int year) {
        if (user == null) {
            return;
        }
        for (LeaveType type : leaveTypeRepository.findByActiveTrueOrderByLeaveNameAsc()) {
            boolean exists = balanceRepository
                    .findByUserIdAndLeaveType_IdAndYear(user.getId(), type.getId(), year)
                    .isPresent();
            if (!exists) {
                balanceRepository.save(new LeaveBalance(user, type, year, type.getAnnualEntitlement()));
            }
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal availableDays(Long userId, Long leaveTypeId, int year) {
        return balanceRepository.findByUserIdAndLeaveType_IdAndYear(userId, leaveTypeId, year)
                .map(LeaveBalance::getAvailableDays)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalance> balancesForYear(Long userId, int year) {
        return balanceRepository.findByUserAndYear(userId, year);
    }

    /**
     * Deducts approved days. Fails with 400 when the balance is insufficient,
     * which rolls the whole approval transaction back.
     */
    @Transactional
    public void deduct(Long userId, LeaveType leaveType, int year, BigDecimal days) {
        LeaveBalance balance = lockOrCreate(userId, leaveType, year);
        if (balance.getAvailableDays().compareTo(days) < 0) {
            throw ApiException.badRequest(
                    "Insufficient leave balance for " + leaveType.getLeaveName()
                            + ": requested " + days.stripTrailingZeros().toPlainString()
                            + " day(s), available "
                            + balance.getAvailableDays().stripTrailingZeros().toPlainString() + " day(s)");
        }
        applyDelta(balance, days);
    }

    /** Gives days back when an approved request is cancelled. */
    @Transactional
    public void restore(Long userId, LeaveType leaveType, int year, BigDecimal days) {
        LeaveBalance balance = lockOrCreate(userId, leaveType, year);
        applyDelta(balance, days.negate());
    }

    private void applyDelta(LeaveBalance balance, BigDecimal delta) {
        BigDecimal used = balance.getUsedDays().add(delta);
        if (used.compareTo(BigDecimal.ZERO) < 0) {
            used = BigDecimal.ZERO;
        }
        if (used.compareTo(balance.getTotalDays()) > 0) {
            used = balance.getTotalDays();
        }
        balance.setUsedDays(used);
        balance.setAvailableDays(balance.getTotalDays().subtract(used));
        balanceRepository.save(balance);
    }

    private LeaveBalance lockOrCreate(Long userId, LeaveType leaveType, int year) {
        return balanceRepository.findForUpdate(userId, leaveType.getId(), year)
                .orElseGet(() -> {
                    User user = new User();
                    user.setId(userId);
                    LeaveBalance created = new LeaveBalance(user, leaveType, year, leaveType.getAnnualEntitlement());
                    return balanceRepository.saveAndFlush(created);
                });
    }
}
