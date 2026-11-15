package dev.gwanho.pleossample

import dev.gwanho.pleossample.Axis.BACKREST
import dev.gwanho.pleossample.Axis.HEIGHT
import dev.gwanho.pleossample.Axis.SLIDE
import dev.gwanho.pleossample.Axis.TILT
import dev.gwanho.pleossample.Command.MoveSeat
import dev.gwanho.pleossample.Command.ReadingLight
import dev.gwanho.pleossample.Command.Say
import dev.gwanho.pleossample.Move.BACKWARD
import dev.gwanho.pleossample.Move.FORWARD
import dev.gwanho.pleossample.Move.STOP
import dev.gwanho.pleossample.Seat.PASSENGER
import dev.gwanho.pleossample.Seat.REAR_LEFT
import dev.gwanho.pleossample.Seat.REAR_RIGHT
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CareSeatEngineTest {
    private val R = REAR_RIGHT
    @Before fun tz() { TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul")) }
    private val R_RIDE = listOf(Ride(0, REAR_RIGHT, 30, false, null, RideKind.BOARD))   // 지난번에 2열 오른쪽에 탐
    private fun engine(confirmed: Boolean = false, places: List<Place> = emptyList(), capable: (Seat, Axis) -> Boolean = { _, _ -> true },
                       rides: List<Ride> = emptyList(), dark: Boolean = true) =
        CareSeatEngine(thresholdMs = 20_000, requiredCount = 2, moveLimitMs = 3_000, lightOffDelayMs = 10_000,
            announceDelayMs = 3_000, pulseOnMs = 400, pulseOffMs = 600, rideBackrestMs = 1_200, exitBackrestMs = 800, boardBackrestMs = 400,
            confirmed = confirmed, places = places, rides = rides, capable = capable, dark = { dark })

    /** 문 열림 → dt 뒤 착좌 → 닫힘 → 한참 뒤 내림. 한 회차 학습용. */
    private fun CareSeatEngine.boarding(seat: Seat, t0: Long, dtMs: Long) {
        onDoor(seat, true, t0); onSeated(seat, true, t0 + dtMs); onDoor(seat, false, t0 + dtMs + 5_000); onSeated(seat, false, t0 + dtMs + 60_000)
    }
    /** 확정된 엔진으로 P에서 보호자가 내린 상태까지. */
    private fun guardianOut(e: CareSeatEngine, t: Long): List<Command> { e.onGear(true, t); e.onDriverSeated(false, t + 1); return e.onDriverDoor(true, t + 2) }
    private fun List<Command>.has(c: Command) = contains(c)

    @Test
    fun `자리와 무관하게 느린 승차를 합산해 두 번이면 확정 대기, 아니요면 처음부터, 기억하면 확정`() {
        val e = engine(); e.onGear(true, 0)
        e.boarding(PASSENGER, 1_000, 22_000)
        assertEquals(1, e.count(PASSENGER)); assertFalse(e.pendingConfirm)
        val out = e.let { it.onDoor(R, true, 200_000); it.onSeated(R, true, 225_000) }
        assertEquals(2, e.totalCount); assertTrue(e.pendingConfirm)
        assertTrue(out.filterIsInstance<Say>().any { "조수석 1번" in it.text && "뒷자리 1번" in it.text && "부모님으로 기억할까요" in it.text })
        e.reject(); assertEquals(0, e.totalCount); assertFalse(e.pendingConfirm)
        e.confirm(); assertTrue(e.isConfirmed)           // F3: 학습 없이도 바로 확정 가능
    }

    @Test
    fun `착좌 없이 닫히면 짐 싣기로 세지 않고, 빨리 타도 세지 않으며, 같은 값 반복은 무시한다`() {
        val e = engine(); e.onGear(true, 0)
        val out = e.let { it.onDoor(R, true, 1_000); it.onDoor(R, false, 31_000) }
        assertEquals(0, e.count(R)); assertTrue(out.filterIsInstance<Command.Log>().any { "제외" in it.text })
        e.boarding(R, 100_000, 5_000); assertEquals(0, e.count(R))
        assertTrue(e.onGear(true, 2).isEmpty()); assertTrue(e.onDoor(R, false, 3).isEmpty())
    }

    @Test
    fun `보호자가 내리면 지난번 탄 자리 하나만 뒤로 빼고 등받이를 조금 젖히며, 탄 기록이 없으면 준비하지 않는다`() {
        val e = engine(confirmed = true, rides = R_RIDE)
        val out = guardianOut(e, 0)
        assertTrue(out.has(MoveSeat(R, SLIDE, BACKWARD))); assertTrue(out.has(Say("잠시만 기다려 주세요.")))
        assertFalse(out.any { it is MoveSeat && it.seat != R })
        val cmds = (100..9_000 step 100).flatMap { e.tick(it.toLong()) }        // 앞뒤 3_000(펄스) → 등받이 400 → DONE
        assertTrue(cmds.has(MoveSeat(R, BACKREST, BACKWARD)))
        assertTrue(cmds.has(Say("이제 타셔도 돼요. 머리 조심하세요.")))
        assertFalse(cmds.any { it is MoveSeat && it.seat != R })
        assertTrue(e.isPrepared(R)); assertEquals(-1f, e.level(R, SLIDE, 9_000), 0f); assertTrue(e.level(R, BACKREST, 9_000) < 0f)
        assertFalse(e.onDriverSeated(true, 10_000).any { it is MoveSeat && it.move == BACKWARD })   // 돌아와도 다시 밀지 않음
        assertFalse(guardianOut(engine(confirmed = true), 0).any { it is MoveSeat })   // 탄 기록이 없으면 어느 자리인지 몰라 준비하지 않음
    }

    @Test
    fun `P가 아니거나 운전석에 사람이 있거나 오늘은 움직이지 않기면 밀지 않고, P 해제에 즉시 멈춘다`() {
        val e = engine(confirmed = true); e.onGear(false, 0); e.onDriverSeated(false, 1)
        assertFalse(e.onDriverDoor(true, 2).any { it is MoveSeat })
        val e2 = engine(confirmed = true); e2.onGear(true, 0); e2.onDriverSeated(true, 1)
        assertFalse(e2.onDriverDoor(true, 2).any { it is MoveSeat })
        val e3 = engine(confirmed = true); e3.setPaused(true, 0)
        assertFalse(guardianOut(e3, 0).any { it is MoveSeat }); assertTrue(e3.paused)
        val e4 = engine(confirmed = true, rides = R_RIDE); guardianOut(e4, 0)
        val out = e4.onGear(false, 1_000); assertTrue(out.has(MoveSeat(R, SLIDE, STOP)))
        val e5 = engine(confirmed = true); guardianOut(e5, 0); e5.onDoor(R, true, 1_000); e5.onDoor(R, false, 2_000); e5.onGear(false, 3_000)
        assertFalse(e5.tick(20_000).has(ReadingLight(R, false)))                 // 주행 중에는 소등도 미룸
        e5.onGear(true, 30_000); assertTrue(e5.tick(30_000).has(ReadingLight(R, false)))
    }

    @Test
    fun `뒷문이 열리면 그 자리를 뒤로 빼고 등받이를 젖히며 독서등을 켜고, 조수석은 두며, 착좌에 그 자리 이동을 멈춘다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        val open = e.onDoor(R, true, 1_000)
        assertTrue(open.has(ReadingLight(R, true))); assertTrue(open.has(MoveSeat(R, SLIDE, BACKWARD)))
        assertFalse(open.any { it is MoveSeat && it.seat == PASSENGER }); assertFalse(open.has(MoveSeat(R, SLIDE, STOP)))   // 넓히는 쪽은 계속(9.5)
        assertEquals(listOf("뒤로", "등받이"), e.signal(1_100)!!.steps)
        val sit = e.onSeated(R, true, 1_500)
        assertTrue(sit.has(MoveSeat(R, SLIDE, STOP)))
        assertFalse(e.tick(2_000).any { it is MoveSeat })                           // 앉으면 남은 등받이 단계도 하지 않음
        val e2 = engine(confirmed = true); guardianOut(e2, 0); e2.onDoor(R, true, 1_000)
        val cmds = (1_100..20_000 step 100).flatMap { e2.tick(it.toLong()) }       // 문이 열린 채 아무도 안 앉으면 끝까지
        assertTrue(cmds.has(MoveSeat(R, BACKREST, BACKWARD))); assertFalse(cmds.has(ReadingLight(R, false)))   // 문이 열린 동안은 소등하지 않음
        assertEquals(SignalKind.DONE, e2.signal(20_000)!!.kind)                     // 문이 열려 있으면 DONE 유지
    }

    @Test
    fun `조수석 문이 열리면 조수석을 뒤로 빼고 뒷자리는 그대로`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        val out = e.onDoor(PASSENGER, true, 4_000)
        assertTrue(out.has(MoveSeat(PASSENGER, SLIDE, BACKWARD))); assertTrue(out.has(ReadingLight(PASSENGER, true)))
        assertFalse(out.any { it is MoveSeat && it.seat != PASSENGER })
    }

    @Test
    fun `앉고 문이 닫히면 예고 뒤 3초에 등받이를 짧게 끊어 세우고, 이어서 물러난 앞뒤를 되돌린 뒤 독서등을 늦게 끈다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500)                     // 500ms 뒤로 간 뒤 정지
        val closed = e.onDoor(R, false, 2_000)
        assertTrue(closed.filterIsInstance<Say>().any { it.text == "등받이를 세울게요. 출발은 '딩동' 뒤에 해 주세요." }); assertTrue(e.isAnnouncing(R))
        assertFalse(closed.any { it is MoveSeat })
        assertFalse(e.tick(4_900).any { it is MoveSeat })
        assertTrue(e.tick(5_000).has(MoveSeat(R, BACKREST, FORWARD)))           // 2_000 + 3_000
        assertTrue(e.tick(5_400).has(MoveSeat(R, BACKREST, STOP)))              // 400 on
        assertFalse(e.tick(5_900).any { it is MoveSeat })                        // 600 off
        assertTrue(e.tick(6_000).has(MoveSeat(R, BACKREST, FORWARD)))
        assertTrue(e.tick(6_400).has(MoveSeat(R, BACKREST, STOP)))
        assertTrue(e.tick(7_000).has(MoveSeat(R, BACKREST, FORWARD)))
        val end = e.tick(7_400)
        assertTrue(end.has(MoveSeat(R, BACKREST, STOP)))                          // 3 × 400 = 1_200 완료
        assertTrue(end.has(MoveSeat(R, SLIDE, FORWARD)))                          // 같은 틱에 앞뒤 복귀 시작(펄스)
        assertTrue(e.tick(7_800).has(MoveSeat(R, SLIDE, STOP)))
        assertTrue(e.tick(8_400).has(MoveSeat(R, SLIDE, FORWARD)))
        assertTrue(e.tick(8_800).has(MoveSeat(R, SLIDE, STOP)))                  // 400 + 100 = 500
        assertFalse(e.tick(11_000).any { it is MoveSeat })
        assertTrue(e.backrestLevel(R) > 0f); assertEquals(0f, e.slideProgress(R, 11_000))
        assertTrue(e.tick(20_098).has(ReadingLight(R, false)))                   // 끝난 뒤 10초
    }

    @Test
    fun `예고 중에 문이 열리거나 P가 풀리면 등받이를 움직이지 않는다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onDoor(R, false, 2_000)
        e.onDoor(R, true, 3_000); assertFalse(e.isAnnouncing(R))
        assertFalse(e.tick(5_100).any { it is MoveSeat && it.axis == BACKREST })
        val e2 = engine(confirmed = true); guardianOut(e2, 0)
        e2.onDoor(R, true, 1_000); e2.onSeated(R, true, 1_500); e2.onDoor(R, false, 2_000)
        e2.onDriverDoor(false, 2_500); e2.onDriverSeated(true, 2_600); e2.onGear(false, 2_700)
        assertFalse(e2.tick(5_100).any { it is MoveSeat })
    }

    @Test
    fun `주행 뒤 P에 들어오면 앉은 자리 등받이를 더 세우고 앞뒤를 다시 넓히며, 빈 자리에는 하지 않는다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onDoor(R, false, 2_000)
        for (t in 5_000..11_000 step 100) e.tick(t.toLong())                     // 가는 동안 자세(등받이 + 앞뒤 복귀) 완료
        assertEquals(0f, e.slideProgress(R, 11_000))
        e.onDriverDoor(false, 12_000); e.onDriverSeated(true, 12_100); e.onGear(false, 13_000)
        val arrive = e.onGear(true, 100_000)
        assertTrue(arrive.filterIsInstance<Say>().any { it.text == "등받이를 세울게요. 문은 '딩동' 뒤에 열어 주세요." }); assertTrue(e.isAnnouncing(R)); assertFalse(e.isAnnouncing(REAR_LEFT))
        assertTrue(e.tick(103_000).has(MoveSeat(R, BACKREST, FORWARD)))
        assertTrue(e.tick(103_400).has(MoveSeat(R, BACKREST, STOP)))
        assertTrue(e.tick(104_000).has(MoveSeat(R, BACKREST, FORWARD)))
        val end = e.tick(104_400)
        assertTrue(end.has(MoveSeat(R, BACKREST, STOP)))                          // 2 × 400 = 800 완료
        assertTrue(end.has(MoveSeat(R, HEIGHT, FORWARD)))                         // 이어서 높이
        for (t in 104_800..110_000 step 100) e.tick(t.toLong()); assertTrue(e.slideProgress(R, 110_000) > 0f)
        assertFalse(e.isAnnouncing(REAR_LEFT)); assertFalse(e.isMoving(REAR_LEFT))
    }

    @Test
    fun `조수석에 탄 사람은 출발 전 자리를 되돌리고, 내릴 때는 뒷자리가 비어 있을 때만 뒤로 넓힌다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(PASSENGER, true, 4_000); e.onSeated(PASSENGER, true, 5_000)      // 1_000ms 뒤로
        val closed = e.onDoor(PASSENGER, false, 6_000)
        assertTrue(closed.filterIsInstance<Say>().any { it.text == "등받이를 세울게요. 출발은 '딩동' 뒤에 해 주세요." })
        for (t in 9_000..14_000 step 100) e.tick(t.toLong())
        assertEquals(0f, e.slideProgress(PASSENGER, 14_000))
        e.onDriverDoor(false, 15_000); e.onDriverSeated(true, 15_100); e.onGear(false, 16_000)
        assertTrue(e.onGear(true, 100_000).filterIsInstance<Say>().any { it.text == "등받이를 세울게요. 문은 '딩동' 뒤에 열어 주세요." })
        val cmds = (103_000..115_000 step 100).flatMap { e.tick(it.toLong()) }
        assertTrue(cmds.has(MoveSeat(PASSENGER, SLIDE, BACKWARD)))

        val e2 = engine(confirmed = true); guardianOut(e2, 0)
        e2.onDoor(PASSENGER, true, 4_000); e2.onSeated(PASSENGER, true, 5_000); e2.onDoor(PASSENGER, false, 6_000)
        e2.onDoor(REAR_LEFT, true, 7_000); e2.onSeated(REAR_LEFT, true, 7_500); e2.onDoor(REAR_LEFT, false, 8_000)
        for (t in 9_000..22_000 step 100) e2.tick(t.toLong())                    // 2열 좌 3_000ms 복귀까지
        assertEquals(0f, e2.slideProgress(REAR_LEFT, 22_000))
        e2.onDriverDoor(false, 23_000); e2.onDriverSeated(true, 23_100); e2.onGear(false, 24_000)
        e2.onGear(true, 100_000)
        val c2 = (103_000..115_000 step 100).flatMap { e2.tick(it.toLong()) }
        assertFalse(c2.has(MoveSeat(PASSENGER, SLIDE, BACKWARD)))                 // 뒷사람 발 끼임 → 조수석은 등받이만
        assertTrue(c2.has(MoveSeat(REAR_LEFT, SLIDE, BACKWARD)))
    }

    @Test
    fun `보호자가 복귀하면 안 쓴 자리만 물러난 만큼 축마다 원위치하고, 앉아 있는 자리는 둔다`() {
        val e = engine(confirmed = true, rides = listOf(Ride(0, PASSENGER, 30, false, null, RideKind.BOARD))); guardianOut(e, 0)   // 조수석 준비 시작
        for (t in 100..9_000 step 100) e.tick(t.toLong())                        // 조수석 앞뒤 3_000 + 등받이 400 끝
        e.onDoor(R, true, 10_000); e.onSeated(R, true, 10_500); e.onDoor(R, false, 11_000)   // 이번엔 뒷자리에 탐
        e.onDriverDoor(false, 20_000)
        val back = e.onDriverSeated(true, 20_100)
        assertTrue(back.has(MoveSeat(PASSENGER, SLIDE, FORWARD))); assertTrue(back.filterIsInstance<Say>().any { it.text == "자리를 원래대로 돌릴게요." })
        assertFalse(back.any { it is MoveSeat && it.seat == R })
        assertTrue(e.isRestoring(PASSENGER))
        assertFalse(e.tick(23_000).has(MoveSeat(PASSENGER, SLIDE, STOP)))
        val slideDone = e.tick(23_100)
        assertTrue(slideDone.has(MoveSeat(PASSENGER, SLIDE, STOP))); assertTrue(slideDone.has(MoveSeat(PASSENGER, BACKREST, FORWARD)))   // 이어서 등받이
        assertTrue(e.tick(23_500).has(MoveSeat(PASSENGER, BACKREST, STOP)))
        assertFalse(e.isRestoring(PASSENGER)); assertEquals(0f, e.slideProgress(PASSENGER, 23_500))
    }

    @Test
    fun `보호자가 운전석에 있을 때 내리고 문이 닫히면, 그 자리 앞뒤는 넓힌 만큼, 등받이는 세운 만큼 되돌리며 한 문장 말한다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onDoor(R, false, 5_000)
        for (t in 8_000..14_000 step 100) e.tick(t.toLong())                     // 가는 동안: 등받이 1_200 + 앞뒤 500 복귀
        e.onDriverDoor(false, 15_000); e.onDriverSeated(true, 15_100); e.tick(18_100); e.onGear(false, 19_000)
        e.onGear(true, 100_000); for (t in 103_000..115_000 step 100) e.tick(t.toLong())   // 내릴 때: 등받이 800 + 높이·기울기 + 뒤로 3_000
        e.onDoor(R, true, 116_000)
        assertFalse(e.onSeated(R, false, 117_000).any { it is MoveSeat })        // 문 아직 열림
        val closed = e.onDoor(R, false, 118_000)
        assertTrue(closed.has(MoveSeat(R, SLIDE, FORWARD)))                       // 먼저 앞뒤
        assertTrue(closed.filterIsInstance<Say>().any { it.text == "자리를 원래대로 돌릴게요." })
        val slideDone = e.tick(121_000)                                           // 3_000 만큼
        assertTrue(slideDone.has(MoveSeat(R, SLIDE, STOP)))
        assertTrue(slideDone.has(MoveSeat(R, BACKREST, BACKWARD)))                // 같은 틱에 등받이 원위치 시작
        assertTrue(e.tick(123_000).has(MoveSeat(R, BACKREST, STOP)))             // 1_200 + 800
        for (t in 123_000..129_000 step 100) e.tick(t.toLong())                   // 높이·기울기 원위치
        assertFalse(e.isRestoring(R)); assertEquals(0f, e.backrestLevel(R)); assertEquals(0f, e.slideProgress(R, 129_000))
    }

    @Test
    fun `내릴 때는 등받이 → 높이 → 쿠션 앞 내림 → 뒤로 순서로 움직이고, 내린 뒤 원위치는 축마다 반대로 돌린다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onDoor(R, false, 2_000)
        for (t in 5_000..12_000 step 100) e.tick(t.toLong())                      // 가는 동안 자세 끝
        e.onDriverDoor(false, 13_000); e.onDriverSeated(true, 13_100); e.onGear(false, 14_000)
        val arrive = e.onGear(true, 100_000)
        assertTrue(arrive.filterIsInstance<Say>().any { it.text == "등받이를 세울게요. 문은 '딩동' 뒤에 열어 주세요." })
        assertEquals(listOf("등받이", "높이", "쿠션 기울기", "뒤로"), e.signal(100_100)!!.steps)
        val cmds = (103_000..115_000 step 100).flatMap { e.tick(it.toLong()) }.filterIsInstance<MoveSeat>().filter { it.seat == R && it.move != STOP }
        assertEquals(listOf(MoveSeat(R, BACKREST, FORWARD), MoveSeat(R, HEIGHT, FORWARD), MoveSeat(R, TILT, BACKWARD), MoveSeat(R, SLIDE, BACKWARD)), cmds.distinct())
        assertTrue(e.level(R, HEIGHT, 115_000) > 0f); assertTrue(e.level(R, TILT, 115_000) < 0f)
        e.onDoor(R, true, 120_000); e.onSeated(R, false, 121_000)
        val closed = e.onDoor(R, false, 122_000)
        assertTrue(closed.has(MoveSeat(R, SLIDE, FORWARD)))                       // 앞뒤 먼저
        val restore = (122_000..135_000 step 100).flatMap { e.tick(it.toLong()) }.filterIsInstance<MoveSeat>().filter { it.seat == R && it.move != STOP }
        assertEquals(listOf(MoveSeat(R, BACKREST, BACKWARD), MoveSeat(R, HEIGHT, BACKWARD), MoveSeat(R, TILT, FORWARD)), restore.distinct())
        assertEquals(0f, e.level(R, HEIGHT, 135_000)); assertEquals(0f, e.level(R, TILT, 135_000)); assertEquals(0f, e.backrestLevel(R))
    }

    @Test
    fun `높이·기울기 capability가 없는 차는 그 단계를 건너뛴다`() {
        val e = engine(confirmed = true, capable = { _, a -> a == SLIDE || a == BACKREST }); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onDoor(R, false, 2_000)
        for (t in 5_000..12_000 step 100) e.tick(t.toLong())
        e.onDriverDoor(false, 13_000); e.onDriverSeated(true, 13_100); e.onGear(false, 14_000); e.onGear(true, 100_000)
        val cmds = (103_000..112_000 step 100).flatMap { e.tick(it.toLong()) }.filterIsInstance<MoveSeat>().filter { it.seat == R }
        assertFalse(cmds.any { it.axis == HEIGHT || it.axis == TILT }); assertTrue(cmds.has(MoveSeat(R, SLIDE, BACKWARD)))
    }

    @Test
    fun `capability가 없는 축은 명령을 내지 않는다`() {
        val noLeftSlide: (Seat, Axis) -> Boolean = { s, a -> !(s == REAR_LEFT && a == SLIDE) }
        val e = engine(confirmed = true, capable = noLeftSlide, rides = listOf(Ride(0, REAR_LEFT, 30, false, null, RideKind.BOARD)))
        assertFalse(guardianOut(e, 0).any { it is MoveSeat && it.axis == SLIDE })
        assertFalse(e.onDoor(REAR_LEFT, true, 1_000).any { it is MoveSeat && it.axis == SLIDE })
        val e2 = engine(confirmed = true, capable = noLeftSlide, rides = R_RIDE)
        assertTrue(guardianOut(e2, 0).has(MoveSeat(REAR_RIGHT, SLIDE, BACKWARD)))
    }

    @Test
    fun `느린 승차마다 200m 안의 장소에 세고 없으면 새 장소를 만들며, 장소마다 도착 안내를 한다`() {
        val e = engine(); e.onGear(true, 0)
        e.onLocation(37.4979, 127.0276)
        e.boarding(R, 1_000, 22_000)
        assertEquals(1, e.places.size); assertEquals("알아본 곳 1", e.places[0].name); assertEquals(1, e.places[0].count)
        assertEquals(1, e.rides[0].placeId)
        e.onLocation(37.55, 127.10); e.boarding(R, 200_000, 22_000)              // 10km 밖 → 새 장소
        assertEquals(2, e.places.size); assertEquals(2, e.count(R))
        e.onLocation(37.4985, 127.0280); e.boarding(R, 400_000, 22_000)          // 80m 안 → 첫 장소에 합산
        assertEquals(2, e.places.size); assertEquals(2, e.places[0].count); assertEquals(400_000 + 22_000, e.places[0].lastAt)
        e.renamePlace(1, "집 앞"); assertEquals("집 앞", e.places[0].name)
        e.confirm()
        e.onGear(false, 600_000)
        assertTrue(e.onArrive(700_000).isEmpty())                                 // 빈 차: 안내 없음
        e.onGear(true, 800_000); e.onDoor(R, true, 801_000); e.onSeated(R, true, 802_000); e.onDoor(R, false, 803_000)
        e.onGear(false, 900_000)
        assertTrue(e.onArrive(901_000).any { it is Say && "곧 도착해요" in it.text }); assertTrue(e.onArrive(902_000).isEmpty())
        e.removePlace(1); assertEquals(listOf(2), e.places.map { it.id })
        e.onSeated(R, false, 950_000); e.onLocation(37.60, 127.20); e.boarding(PASSENGER, 1_000_000, 22_000)   // 지운 뒤 새 장소 → id 겹치지 않음
        assertEquals(listOf(2, 3), e.places.map { it.id }); assertEquals("알아본 곳 3", e.places[1].name)
        val e2 = CareSeatEngine(places = e.places, rides = e.rides)
        assertEquals("알아본 곳 3", e2.places[1].name)
    }

    @Test
    fun `같은 요일 같은 30분 칸에 세 번 쌓이면 알아본 시간대가 되고, 예상은 가장 많은 자리와 시간대다`() {
        val e = engine(); e.onGear(true, 0)
        // 2026-11-03(화) 14:30 KST = 1793683800000ms 기준, 매주 같은 시각 +7일
        val tue1430 = 1_793_683_800_000L; val week = 7 * 24 * 3600_000L
        e.boarding(PASSENGER, tue1430, 22_000)
        e.boarding(PASSENGER, tue1430 + week + 10 * 60_000, 22_000)             // 14:40 → 같은 칸(29)
        assertTrue(e.learnedBands().isEmpty())
        e.boarding(PASSENGER, tue1430 + 2 * week - 20 * 60_000, 22_000)         // 14:10 → 칸 28, 앞뒤 한 칸 안 → 28·29가 겹쳐 이른 칸 28 하나만
        val b = e.learnedBands().single()
        assertEquals(3, b.weekday); assertEquals(28, b.slot); assertEquals(3, b.count)   // Calendar 화요일 = 3
        assertEquals(PASSENGER to b, e.prediction())
    }

    @Test
    fun `보호자가 내린 지 60초 안에 빨리 앉으면 도움받은 탑승으로 세고, 기록에 남는다`() {
        val e = engine(); e.onGear(true, 0)
        e.onDriverSeated(false, 10_000)                                           // 보호자 하차
        e.onDoor(R, true, 20_000); e.onSeated(R, true, 25_000)                    // 5초 만에 앉음(기준 20초 미만)
        assertEquals(1, e.count(R))
        assertEquals(1, e.rides.size)
        assertEquals(Ride(25_000, R, 5, true, null, RideKind.BOARD), e.rides[0])
        e.onDoor(R, false, 26_000); e.onDriverSeated(true, 30_000); e.onSeated(R, false, 60_000)
        e.onDoor(PASSENGER, true, 200_000); e.onSeated(PASSENGER, true, 205_000)  // 보호자 앉아 있음 → 도움 아님 → 세지 않음(기록에는 남음)
        assertEquals(0, e.count(PASSENGER)); assertEquals(2, e.rides.size); assertFalse(e.rides[1].helped)
        val e2 = engine(); e2.onGear(true, 0); e2.onDriverSeated(false, 0)
        e2.onDoor(R, true, 70_000); e2.onSeated(R, true, 75_000)                 // 비운 지 75초 → 간격 밖
        assertEquals(0, e2.count(R))
    }

    @Test
    fun `확정 뒤에는 탈 때와 내릴 때 걸린 시간을 기록하고 학습 횟수는 늘리지 않는다`() {
        val e = engine(confirmed = true); e.onGear(true, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 31_000); e.onDoor(R, false, 32_000)
        assertEquals(listOf(Ride(31_000, R, 30, false, null, RideKind.BOARD)), e.rides)
        assertEquals(0, e.count(R))
        e.onDoor(R, true, 100_000); e.onSeated(R, false, 108_000); e.onDoor(R, false, 115_000)
        assertEquals(Ride(115_000, R, 7, false, null, RideKind.EXIT), e.rides[1])  // 착좌 해제→문 닫힘 7초
        val e3 = CareSeatEngine(rides = e.rides)
        assertEquals(2, e3.rides.size)                                            // 생성자로 복원
    }

    @Test
    fun `신호는 넓히는 동안 WAIT, 예고 동안 SOON과 카운트다운, 끝나면 3초 DONE이고 문이 열려 있으면 DONE을 유지한다 - 조수석이 못 움직이는 차`() {
        val e = engine(confirmed = true, capable = { s, a -> !(s == PASSENGER && a == SLIDE) }, rides = R_RIDE); guardianOut(e, 0)   // 2열 오른쪽 준비 시작
        val w = e.signal(1_000)!!
        assertEquals(SignalKind.WAIT, w.kind); assertEquals(SignalCtx.BOARD, w.ctx); assertEquals(listOf("뒤로", "등받이"), w.steps); assertEquals(0, w.stepIndex)
        val cmds = (100..8_000 step 100).flatMap { e.tick(it.toLong()) }        // 앞뒤 3_000(펄스, 7_300 끝) → 등받이 400(7_700 끝) → DONE
        assertTrue(cmds.filterIsInstance<Say>().any { it.text == "이제 타셔도 돼요. 머리 조심하세요." })
        assertEquals(SignalKind.DONE, e.signal(8_000)!!.kind)
        assertEquals(null, e.signal(11_000))                                      // 3초 뒤 닫힘
        assertTrue(e.isPrepared(REAR_RIGHT))
        e.onDoor(R, true, 12_000)                                                 // 이미 넓혀 둔 자리의 문이 열림 → 06
        assertEquals(SignalKind.DONE, e.signal(12_500)!!.kind); assertEquals(R, e.signal(12_500)!!.seat)
        e.onSeated(R, true, 13_000); e.onDoor(R, false, 14_000)                   // 예고
        val s = e.signal(14_100)!!
        assertEquals(SignalKind.SOON, s.kind); assertEquals(SignalCtx.RIDE, s.ctx); assertEquals(3, s.countdown); assertEquals(listOf("등받이", "앞으로"), s.steps)
        assertEquals(1, e.signal(16_500)!!.countdown)
        e.tick(17_000)                                                            // 등받이 시작
        val r = e.signal(17_100)!!
        assertEquals(SignalKind.WAIT, r.kind); assertEquals(0, r.stepIndex)
        for (t in 17_100..19_500 step 100) e.tick(t.toLong())                     // 등받이 1_200 끝(19_400), 앞으로 3_000 시작
        assertEquals(1, e.signal(19_500)!!.stepIndex)
        for (t in 19_600..26_700 step 100) e.tick(t.toLong())                     // 400ms 펄스 x7.5 → 26_600 끝
        assertEquals(SignalKind.DONE, e.signal(26_700)!!.kind); assertEquals(SignalCtx.RIDE, e.signal(26_700)!!.ctx)
    }

    @Test
    fun `등받이를 세우는 중에 출발하면 멈추고 pending으로 남으며, 다음 P에서 내릴 때 자세로 이어진다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onDoor(R, false, 2_000)
        e.tick(5_000)                                                             // 등받이 시작
        val out = e.onGear(false, 5_200)
        assertTrue(out.has(MoveSeat(R, BACKREST, STOP)))
        assertEquals(Pending(R, SignalCtx.RIDE), e.pending())
        assertEquals(null, e.signal(6_000))                                       // 주행 중 신호 없음
        e.onGear(true, 50_000)
        assertEquals(null, e.pending())
        assertTrue(e.isAnnouncing(R)); assertEquals(SignalCtx.EXIT, e.signal(50_100)!!.ctx)
        e.tick(53_000)                                                            // 내릴 때 등받이 시작
        e.onGear(false, 53_200)                                                   // 또 출발
        assertEquals(Pending(R, SignalCtx.EXIT), e.pending())
        e.onGear(true, 90_000)
        assertTrue(e.isAnnouncing(R)); assertEquals(SignalCtx.EXIT, e.signal(90_100)!!.ctx)   // 다시 이어서
    }

    @Test
    fun `원위치가 남은 빈 자리로 출발하면 RESTORE pending이고, 다음 P에서 예고 없이 원위치한다`() {
        val e = engine(confirmed = true, rides = R_RIDE); guardianOut(e, 0); e.tick(1_000)   // 2열 오른쪽 뒤로 가는 중
        e.onDriverDoor(false, 2_000); e.onDriverSeated(true, 2_100)               // 준비가 끝나기 전에 보호자 복귀 → 출발
        e.onGear(false, 5_000)
        assertEquals(Pending(R, SignalCtx.RESTORE), e.pending())
        val out = e.onGear(true, 60_000)
        assertTrue(out.has(MoveSeat(R, SLIDE, FORWARD)))
        assertFalse(out.any { it is Say }); assertEquals(null, e.pending())
    }

    @Test
    fun `알아본 장소 밖에서 빨리 앉으면 묻고, 예면 가는 동안 자세, 아니요면 이번 정차는 움직이지 않는다`() {
        val e = engine(confirmed = true, places = listOf(Place(1, 37.4979, 127.0276, "집 앞", 2, 0, 0)))
        e.onGear(true, 0); e.onLocation(37.55, 127.10)                           // 10km 밖
        e.onDoor(PASSENGER, true, 1_000)
        assertTrue(e.onSeated(PASSENGER, true, 4_000).has(Say("부모님 타셨어요? 화면에서 알려 주세요.")))   // 3초 만에, 보호자 앉아 있음
        assertEquals(PASSENGER, e.question)
        e.onDoor(PASSENGER, false, 5_000)
        assertFalse(e.isAnnouncing(PASSENGER))                                    // 보류
        e.answerBoarding(true, 6_000)
        assertEquals(null, e.question); assertTrue(e.isAnnouncing(PASSENGER))
        val e2 = engine(confirmed = true, places = listOf(Place(1, 37.4979, 127.0276, "집 앞", 2, 0, 0)))
        e2.onGear(true, 0); e2.onLocation(37.55, 127.10)
        e2.onDoor(PASSENGER, true, 1_000); e2.onSeated(PASSENGER, true, 4_000); e2.onDoor(PASSENGER, false, 5_000)
        e2.answerBoarding(false, 6_000)
        assertEquals(null, e2.question); assertFalse(e2.isAnnouncing(PASSENGER))
        assertFalse(e2.tick(20_000).any { it is MoveSeat })
        val e3 = engine(confirmed = true, places = listOf(Place(1, 37.4979, 127.0276, "집 앞", 2, 0, 0)))
        e3.onGear(true, 0); e3.onLocation(37.4980, 127.0277)                     // 장소 안 → 묻지 않음
        e3.onDoor(PASSENGER, true, 1_000); e3.onSeated(PASSENGER, true, 4_000)
        assertEquals(null, e3.question)
        val e4 = engine(confirmed = true)                                         // 장소·시간대 정보 없음 → (c)만
        e4.onGear(true, 0); e4.onDoor(PASSENGER, true, 1_000); e4.onSeated(PASSENGER, true, 25_000)   // 24초 → 느림 → 묻지 않음
        assertEquals(null, e4.question)
    }

    @Test
    fun `설정값을 바꾸면 다음 판정부터 적용되고, 이지 액세스면 앞뒤는 움직이지 않는다`() {
        val e = engine(); e.onGear(true, 0)
        e.thresholdMs = 5_000
        e.boarding(R, 1_000, 6_000); assertEquals(1, e.count(R))
        e.requiredCount = 1; assertTrue(e.pendingConfirm)
        val e2 = engine(confirmed = true); e2.easyAccess = true
        assertFalse(guardianOut(e2, 0).any { it is MoveSeat })
        e2.onDoor(R, true, 1_000); e2.onSeated(R, true, 1_500); e2.onDoor(R, false, 2_000)
        val cmds = (5_000..9_000 step 100).flatMap { e2.tick(it.toLong()) }.filterIsInstance<MoveSeat>()
        assertTrue(cmds.any { it.axis == BACKREST }); assertFalse(cmds.any { it.axis == SLIDE })
    }

    @Test
    fun `자세 시간 람다가 있으면 그 시간과 부호로 움직이고, 수동 이동은 누적되어 원위치 대상이 된다`() {
        val e = CareSeatEngine(confirmed = true, postureMs = { s, p, a -> if (s == REAR_RIGHT && p == Posture.RIDE && a == BACKREST) 600L else null })
        e.onGear(true, 0); e.onDriverSeated(false, 1); e.onDriverDoor(true, 2)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onDoor(R, false, 2_000)
        e.tick(5_000); e.tick(5_400); e.tick(6_000)
        val end = e.tick(6_200)                                                   // 400 + 200 = 600 → 끝
        assertTrue(end.has(MoveSeat(R, BACKREST, STOP)))
        val m = engine(confirmed = true); m.onGear(true, 0)
        assertTrue(m.manual(PASSENGER, HEIGHT, FORWARD, 10_000).has(MoveSeat(PASSENGER, HEIGHT, FORWARD)))
        assertTrue(m.manual(PASSENGER, HEIGHT, STOP, 10_700).has(MoveSeat(PASSENGER, HEIGHT, STOP)))
        assertTrue(m.level(PASSENGER, HEIGHT, 11_000) > 0f)
        assertEquals(null, m.signal(10_300))                                      // 수동은 신호가 아님
        val back = m.onDriverSeated(true, 12_000) + m.onDriverDoor(false, 12_100)  // 보호자 복귀(착좌 + 문 닫힘)
        assertTrue(back.has(MoveSeat(PASSENGER, HEIGHT, BACKWARD)))              // 움직인 700ms만큼 원위치
        m.clearAll(); assertFalse(m.isConfirmed); assertEquals(0, m.rides.size); assertEquals(0, m.places.size)
    }

    @Test
    fun `추우면 앉을 때 열선을 켜고 내리면 끄며, 보호자가 끄면 그 탑승 동안 다시 켜지 않는다`() {
        var cold = true
        val e = CareSeatEngine(confirmed = true, cold = { cold }); e.onGear(true, 0)
        e.onDoor(PASSENGER, true, 1_000)
        assertTrue(e.onSeated(PASSENGER, true, 2_000).has(Command.SeatHeat(PASSENGER, 2)))
        e.onHeat(PASSENGER, 0)                                                    // 보호자가 끔
        e.onSeated(PASSENGER, false, 3_000); assertTrue(e.onSeated(PASSENGER, true, 4_000).none { it is Command.SeatHeat })
        e.onSeated(PASSENGER, false, 5_000); e.onDoor(PASSENGER, false, 5_500); e.onDoor(PASSENGER, true, 6_000)
        assertTrue(e.onSeated(PASSENGER, true, 7_000).has(Command.SeatHeat(PASSENGER, 2)))   // 문을 다시 연 새 탑승
        assertTrue(e.onSeated(PASSENGER, false, 8_000).has(Command.SeatHeat(PASSENGER, 0)))
        cold = false; e.heatEnabled = false
        assertTrue(e.onSeated(PASSENGER, true, 9_000).none { it is Command.SeatHeat })
    }

    @Test
    fun `벨트 신호가 있으면 매야 가는 동안 자세를 예고하고, 내릴 때 자세 뒤 벨트를 풀면 일어서는 법을 말한다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onBelt(R, false, 1_600); e.onDoor(R, false, 2_000)
        assertFalse(e.isAnnouncing(R))                                            // 벨트 안 맴
        val belt = e.onBelt(R, true, 3_000)
        assertTrue(e.isAnnouncing(R)); assertTrue(belt.filterIsInstance<Say>().any { it.text == "등받이를 세울게요. 출발은 '딩동' 뒤에 해 주세요." })
        for (t in 6_000..12_000 step 100) e.tick(t.toLong())
        e.onDriverDoor(false, 13_000); e.onDriverSeated(true, 13_100); e.onGear(false, 14_000); e.onGear(true, 100_000)
        for (t in 103_000..115_000 step 100) e.tick(t.toLong())
        assertTrue(e.onBelt(R, false, 116_000).filterIsInstance<Say>().any { "두 발이 땅에 닿은 뒤" in it.text })
    }

    @Test
    fun `이동이 실패하면 그 자리의 남은 단계를 버리고 DONE으로 넘기며, 도착 안내 시각을 남긴다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onDoor(R, false, 2_000); e.tick(5_000)   // 등받이 시작
        val out = e.onMoveFailed(R, BACKREST, 5_200)
        assertTrue(out.has(MoveSeat(R, BACKREST, STOP)))
        assertEquals(SignalKind.DONE, e.signal(5_300)!!.kind); assertFalse(e.tick(8_000).any { it is MoveSeat && it.seat == R })
        assertEquals(null, e.arrivedAt)
        e.onDriverDoor(false, 9_000); e.onDriverSeated(true, 9_100); e.onGear(false, 10_000)
        e.onArrive(20_000); assertEquals(20_000L, e.arrivedAt)
    }

    @Test
    fun `멈춤 시각을 넘기면 끊긴 이동이 움직인 시간만큼 남아, 복귀 때 그만큼만 원위치한다`() {
        val e = engine(confirmed = true, rides = R_RIDE); guardianOut(e, 0)       // 2열 오른쪽 넓힘 t=2부터
        e.setPaused(true, 1_500); e.setPaused(false, 1_600)                       // 1_498ms 움직임
        e.onDriverDoor(false, 2_000)
        val out = e.onDriverSeated(true, 2_100)
        assertTrue(out.has(MoveSeat(R, SLIDE, FORWARD))); assertTrue(e.isRestoring(R))
        assertFalse(e.tick(3_597).has(MoveSeat(R, SLIDE, STOP)))
        assertTrue(e.tick(3_598).has(MoveSeat(R, SLIDE, STOP)))
    }

    @Test
    fun `아니요나 미답이면 도착해도 내릴 때 자세를 하지 않는다`() {
        val places = listOf(Place(1, 37.4979, 127.0276, "집 앞", 2, 0, 0))
        for (answer in listOf(true, false)) {
            val e = engine(confirmed = true, places = places)
            e.onGear(true, 0); e.onLocation(37.55, 127.10)
            e.onDoor(PASSENGER, true, 1_000); e.onSeated(PASSENGER, true, 4_000); e.onDoor(PASSENGER, false, 5_000)
            assertEquals(PASSENGER, e.question)
            if (answer) e.answerBoarding(false, 6_000)
            e.onGear(false, 10_000); e.onGear(true, 60_000)
            assertFalse(e.isAnnouncing(PASSENGER))
            for (t in 60_000..70_000 step 100) assertFalse(e.tick(t.toLong()).any { it is MoveSeat })
        }
    }

    @Test
    fun `주행 중에는 STOP이 거부되어 끊긴 이동은 상한까지 간 것으로 보고, 다음 P에서 STOP을 한 번 더 보낸 뒤 원위치한다`() {
        val e = engine(confirmed = true, rides = R_RIDE); guardianOut(e, 0)
        e.onDriverDoor(false, 500); e.onDriverSeated(true, 600); e.tick(402); e.tick(1_002); e.onGear(false, 1_050)   // 준비(t=2부터) 중 두 번째 펄스가 켜진 중에 보호자 복귀 → 출발
        assertEquals(-1f, e.level(R, SLIDE, 1_050), 0f); assertEquals(0f, e.level(REAR_LEFT, SLIDE, 1_050), 0f)   // 상한(-3_000ms)까지 간 것으로
        val out = e.onGear(true, 60_000)
        assertTrue(out.has(MoveSeat(R, SLIDE, STOP)))
        assertTrue(out.has(MoveSeat(R, SLIDE, FORWARD)))
        assertFalse(e.tick(62_999).has(MoveSeat(R, SLIDE, STOP)))
        assertTrue(e.tick(63_000).has(MoveSeat(R, SLIDE, STOP)))
    }

    @Test
    fun `펄스가 쉬는 동안(이미 STOP) 출발하면 시트는 서 있으므로 움직인 만큼만 셈한다`() {
        val e = engine(confirmed = true, rides = R_RIDE); guardianOut(e, 0)
        e.tick(402)                                                                  // 첫 펄스(t=2~402) 끝 → STOP
        e.onDriverDoor(false, 500); e.onDriverSeated(true, 600); e.onGear(false, 700)   // 쉬는 중에 출발
        assertEquals(-400f / 3_000, e.level(R, SLIDE, 700), 0.001f)
        assertFalse(e.onGear(true, 60_000).has(MoveSeat(R, SLIDE, STOP)))           // 다시 보낼 STOP 없음(바로 원위치)
    }

    @Test
    fun `탔던 자리는 내려서 문이 닫혀도 두었다가, 보호자가 운전석에 돌아오면 원위치한다`() {
        val e = engine(confirmed = true); guardianOut(e, 0)
        e.onDoor(R, true, 1_000); e.onSeated(R, true, 1_500); e.onDoor(R, false, 2_000)
        for (t in 5_000..12_000 step 100) e.tick(t.toLong())                      // 가는 동안 자세
        e.onDriverDoor(false, 13_000); e.onDriverSeated(true, 13_100); e.onGear(false, 14_000); e.onGear(true, 100_000)
        for (t in 103_000..115_000 step 100) e.tick(t.toLong())                   // 내릴 때 자세
        e.onDriverSeated(false, 116_000); e.onDriverDoor(true, 116_100)           // 보호자가 내려 문을 열어 줌
        e.onDoor(R, true, 117_000); e.onSeated(R, false, 118_000)
        val closed = e.onDoor(R, false, 119_000)
        assertFalse(closed.any { it is MoveSeat }); assertFalse(e.isRestoring(R)) // 보호자가 아직 밖
        assertFalse(e.tick(125_000).any { it is MoveSeat })
        e.onDriverDoor(false, 130_000)
        val back = e.onDriverSeated(true, 130_100)
        assertTrue(back.has(MoveSeat(R, SLIDE, FORWARD))); assertTrue(back.filterIsInstance<Say>().any { it.text == "자리를 원래대로 돌릴게요." })
    }

    @Test
    fun `전조등이 꺼져 있으면(낮) 독서등을 켜지 않고, 켜져 있을 때만 켠다`() {
        val day = engine(confirmed = true, dark = false); guardianOut(day, 0)
        assertFalse(day.onDoor(R, true, 1_000).any { it is ReadingLight })
        day.onSeated(R, true, 1_500); assertFalse(day.onDoor(R, false, 2_000).any { it is ReadingLight })   // 예고에도
        assertFalse((2_100..30_000 step 100).flatMap { day.tick(it.toLong()) }.any { it is ReadingLight })
        val night = engine(confirmed = true); guardianOut(night, 0)
        assertTrue(night.onDoor(R, true, 1_000).has(ReadingLight(R, true)))
    }

    @Test
    fun `준비된 자리의 문이 처음 열릴 때 한 번만 신호 설명을 말한다`() {
        val guide = "'띵, 띵' 하는 동안은 기다리시고, '딩동' 하고 초록 불이 켜지면 타세요."
        val e = engine(confirmed = true, rides = R_RIDE); guardianOut(e, 0)
        for (t in 100..9_000 step 100) e.tick(t.toLong())                        // 2열 오른쪽 준비 끝
        assertFalse(e.onDoor(PASSENGER, true, 9_500).has(Say(guide)))             // 막 넓히기 시작한 자리에서는 아직
        e.onDoor(PASSENGER, false, 9_600)
        assertTrue(e.onDoor(R, true, 10_000).has(Say(guide))); assertTrue(e.guided)
        e.onDoor(R, false, 11_000)
        assertFalse(e.onDoor(R, true, 12_000).has(Say(guide)))                    // 두 번째부터는 안 함
        val e2 = engine(); e2.onGear(true, 0)
        assertFalse(e2.onDoor(R, true, 1_000).has(Say(guide)))                    // 확정 전에는 말하지 않음
    }

    @Test
    fun `보호자가 돌아와 앉았다가 다시 내리면 또 준비한다`() {
        val e = engine(confirmed = true, rides = listOf(Ride(0, PASSENGER, 30, false, null, RideKind.BOARD))); guardianOut(e, 0)
        for (t in 100..9_000 step 100) e.tick(t.toLong())
        e.onDriverDoor(false, 10_000); e.onDriverSeated(true, 10_100)             // 복귀 → 조수석 원위치
        for (t in 10_200..14_000 step 100) e.tick(t.toLong())
        assertEquals(0f, e.slideProgress(PASSENGER, 14_000))
        e.onDriverSeated(false, 15_000)
        assertTrue(e.onDriverDoor(true, 15_100).has(MoveSeat(PASSENGER, SLIDE, BACKWARD)))
    }
}
