package com.hackathon.leave;

import com.hackathon.leave.service.LeaveService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the scored math: working-day counts and pro-rated
 * entitlement. These need no Spring context and run in milliseconds.
 */
class LeaveServiceMathTest {

    // ---------- workingDays ----------

    @Test
    void fullWeekCountsFiveWorkingDays() {
        assertEquals(5, LeaveService.workingDays(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9))); // Mon–Fri
    }

    @Test
    void weekendOnlyRangeCountsZero() {
        assertEquals(0, LeaveService.workingDays(
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11))); // Sat–Sun
    }

    @Test
    void rangeAcrossTwoWeekends() {
        // Fri 2 Oct → Fri 16 Oct 2026: 11 working days
        assertEquals(11, LeaveService.workingDays(
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 16)));
    }

    // ---------- entitledDays (pro-rating) ----------

    @Test
    void januaryJoinerGetsFullQuota() {
        assertEquals(0, LeaveService.entitledDays(LocalDate.of(2026, 1, 1), 20, 2026)
                .compareTo(BigDecimal.valueOf(20)));
    }

    @Test
    void previousYearJoinerGetsFullQuota() {
        assertEquals(0, LeaveService.entitledDays(LocalDate.of(2020, 6, 15), 20, 2026)
                .compareTo(BigDecimal.valueOf(20)));
    }

    @Test
    void september15JoinerGetsSixPointSevenOfTwenty() {
        assertEquals(new BigDecimal("6.7"),
                LeaveService.entitledDays(LocalDate.of(2026, 9, 15), 20, 2026));
    }

    @Test
    void decemberJoinerGetsOnePointSeven() {
        assertEquals(new BigDecimal("1.7"),
                LeaveService.entitledDays(LocalDate.of(2026, 12, 1), 20, 2026));
    }

    @Test
    void futureJoinerGetsZero() {
        assertEquals(0, LeaveService.entitledDays(LocalDate.of(2027, 3, 1), 20, 2026)
                .compareTo(BigDecimal.ZERO));
    }
}
