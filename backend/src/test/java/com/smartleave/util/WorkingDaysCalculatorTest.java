package com.smartleave.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingDaysCalculatorTest {

    @Test
    @DisplayName("Monday to Friday counts five working days")
    void countsWeekdays() {
        // 2026-11-02 is a Monday, 2026-11-06 a Friday
        BigDecimal days = WorkingDaysCalculator.calculate(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6));
        assertThat(days).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Saturday and Sunday are never counted")
    void skipsWeekends() {
        // Fri 2026-11-06 -> Mon 2026-11-09 covers Sat+Sun
        BigDecimal days = WorkingDaysCalculator.calculate(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 9));
        assertThat(days).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("A weekend-only range has zero duration")
    void weekendOnlyRange() {
        BigDecimal days = WorkingDaysCalculator.calculate(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8));
        assertThat(days).isEqualByComparingTo("0");
        assertThat(WorkingDaysCalculator.isWeekendOnly(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8))).isTrue();
    }

    @Test
    @DisplayName("Start and end dates are inclusive")
    void inclusiveRange() {
        BigDecimal single = WorkingDaysCalculator.calculate(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4));
        assertThat(single).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("End before start returns zero instead of a negative number")
    void invalidRangeReturnsZero() {
        assertThat(WorkingDaysCalculator.calculate(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 2)))
                .isEqualByComparingTo("0");
        assertThat(WorkingDaysCalculator.calculate(null, LocalDate.of(2026, 11, 2)))
                .isEqualByComparingTo("0");
        assertThat(WorkingDaysCalculator.calculate(LocalDate.of(2026, 11, 2), null))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Two weeks of calendar days equal ten working days")
    void twoWeeks() {
        BigDecimal days = WorkingDaysCalculator.calculate(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 15));
        assertThat(days).isEqualByComparingTo("10");
    }
}
