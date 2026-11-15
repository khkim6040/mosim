package dev.gwanho.pleossample

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {
    @org.junit.Before fun tz() { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul")) }
    @Test
    fun `달별로 탈 때·내릴 때 평균 초를 내고 최근 3달만 돌려준다`() {
        val oct = 1_791_075_600_000L   // 2026-10-04 10:00 KST
        val nov = 1_793_683_800_000L   // 2026-11-03 14:30 KST
        val rides = listOf(
            Ride(oct, Seat.PASSENGER, 18, false, 1, RideKind.BOARD), Ride(oct + 1, Seat.PASSENGER, 20, false, 1, RideKind.EXIT), Ride(oct + 2, Seat.PASSENGER, 28, false, 1, RideKind.EXIT),
            Ride(nov, Seat.PASSENGER, 19, false, 1, RideKind.BOARD), Ride(nov + 1, Seat.PASSENGER, 32, false, 1, RideKind.EXIT),
            Ride(nov + 2, Seat.REAR_LEFT, 50, false, 1, RideKind.EXIT),   // 다른 자리 → 제외
        )
        val m = mobility(rides, Seat.PASSENGER, nov + 10)
        assertEquals(listOf("10월", "11월"), m.map { it.label })
        assertEquals(24, m[0].exit); assertEquals(18, m[0].board); assertEquals(32, m[1].exit); assertEquals(1, m[1].count)
    }
    @Test
    fun `이번 달과 지난달은 달력 기준으로 고른다`() {
        val oct = 1_791_075_600_000L; val nov = 1_793_683_800_000L
        val rides = listOf(
            Ride(oct, Seat.PASSENGER, 18, false, 1, RideKind.BOARD), Ride(oct + 1, Seat.PASSENGER, 20, false, 1, RideKind.EXIT),
            Ride(nov, Seat.PASSENGER, 19, false, 1, RideKind.BOARD), Ride(nov + 1, Seat.PASSENGER, 32, false, 1, RideKind.EXIT),
        )
        val m = mobility(rides, Seat.PASSENGER, nov + 10)
        assertEquals(m[1] to m[0], mobilityCompare(m, nov + 10))
        assertEquals(null to m[1], mobilityCompare(m, 1_796_275_800_000L))
    }
}
