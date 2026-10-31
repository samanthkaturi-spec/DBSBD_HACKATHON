package com.smartleave.util;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Leave duration is always calculated on the backend from Monday to Friday.
 *
 * <p><b>Assumption (documented in the README):</b> public holidays are not
 * configured in this MVP, so only Saturdays and Sundays are excluded.</p>
 */
public final class WorkingDaysCalculator {

    private WorkingDaysCalculator() {
    }

    /**
     * Counts inclusive working days between {@code startDate} and {@code endDate}.
     * Saturdays and Sundays are skipped; the range is inclusive on both ends.
     *
     * @return number of working days (never negative; 0 for an invalid range)
     */
    public static BigDecimal calculate(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return BigDecimal.ZERO;
        }
        long workingDays = 0;
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            DayOfWeek dow = day.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays++;
            }
        }
        return BigDecimal.valueOf(workingDays);
    }

    /** True when every day in the range is a weekend (duration would be zero). */
    public static boolean isWeekendOnly(LocalDate startDate, LocalDate endDate) {
        return calculate(startDate, endDate).signum() == 0;
    }
}
