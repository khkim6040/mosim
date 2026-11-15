package dev.gwanho.pleossample

enum class Seat(val label: String) { PASSENGER("조수석"), REAR_LEFT("2열 왼쪽"), REAR_RIGHT("2열 오른쪽") }
enum class Axis(val label: String) { SLIDE("앞뒤"), BACKREST("등받이"), HEIGHT("높이"), TILT("쿠션 기울기") }
enum class Move { BACKWARD, STOP, FORWARD }
/** 자세 맞추기 화면의 세 자세. */
enum class Posture(val label: String) { BOARD("탈 때"), RIDE("가는 동안"), EXIT("내릴 때") }
enum class RideKind { BOARD, EXIT }
enum class SignalCtx { BOARD, RIDE, EXIT, RESTORE }
enum class SignalKind { SOON, WAIT, DONE }
/** 부모님께 보일 신호 하나. steps = 이번 자세의 단계 이름, stepIndex = 진행 중 단계(끝났으면 steps.size). countdown = SOON 남은 초. */
data class Signal(val seat: Seat, val ctx: SignalCtx, val kind: SignalKind, val since: Long, val steps: List<String>, val stepIndex: Int, val countdown: Int?)
/** 주행 중 띠용. P 해제 때 멈춘 자세(RIDE/EXIT) 또는 남은 원위치(RESTORE). */
data class Pending(val seat: Seat, val ctx: SignalCtx)
/** 알아본 장소. id는 1부터, 이름 기본값 「알아본 곳 n」. */
class Place(val id: Int, val lat: Double, val lon: Double, var name: String, var count: Int, var helpedCount: Int, var lastAt: Long)
/** 요일(Calendar.DAY_OF_WEEK) × 30분 칸(hour*2 + minute/30)에 쌓인 느린 탑승 수. */
data class Band(val weekday: Int, val slot: Int, val count: Int)
/** 탑승 한 번의 기록. seconds = BOARD면 문 열림→착좌, EXIT면 착좌 해제→문 닫힘. helped = 보호자가 내려 도움. */
data class Ride(val at: Long, val seat: Seat, val seconds: Long, val helped: Boolean, val placeId: Int?, val kind: RideKind)

sealed class Command {
    data class MoveSeat(val seat: Seat, val axis: Axis, val move: Move) : Command()
    data class ReadingLight(val seat: Seat, val on: Boolean) : Command()
    data class Log(val text: String) : Command()
    data class SeatHeat(val seat: Seat, val level: Int) : Command()
    data class Say(val text: String) : Command()   // TTS 한 문장
}

/**
 * 돌봄 좌석 상태기계. Android·SDK 의존 없음. 입력은 차량 신호 변화(+시각 ms), 출력은 명령 목록. 같은 값 반복은 무시.
 *
 * 프로필은 「부모님」 하나. 학습은 자리별로 세어 합이 requiredCount면 확정 대기(F1·F2). confirm()은 언제든 가능(F3).
 * 준비: 확정 + P + 운전석 비움 + 운전석 문 열림 → 지난번 탄 자리 하나를 SLIDE BACKWARD, 이어서 BACKREST BACKWARD(boardBackrestMs). 탄 기록이 없으면 하지 않음.
 * 문 열림: 열린 자리가 비어 있고 아직 안 넓혔으면 같은 준비. 열린 자리는 넓히는 쪽만, 착좌에 정지(9.5). 독서등은 dark()일 때만 켬.
 * 가는 동안(F14): 착좌 + 그 문 닫힘 + P → 예고(Say + 독서등) → announceDelayMs 뒤 BACKREST FORWARD 펄스 rideBackrestMs, 이어서 물러난 SLIDE를 원래 자리로.
 * 내릴 때(F15): 주행 뒤 P 진입 + 착좌 → 예고 → BACKREST FORWARD 펄스 exitBackrestMs, 이어서 SLIDE를 넓히는 쪽으로(조수석은 뒷자리가 빌 때만).
 * 원위치(F5): 비어 있고 문 닫힌 자리를 보호자 복귀(운전석 착좌+문 닫힘) 또는 하차(탔던 사람이 내리고 문 닫힘)에, 움직인 방향 반대로 움직인 시간만큼.
 *             뒷자리 사람을 위해 당긴 조수석은 그 사람이 내릴 때 함께 되돌린다.
 * 모든 이동은 P에서만, capable(seat, axis)가 true일 때만. paused면 이동만 멈추고 학습·안내는 계속(F7).
 */
class CareSeatEngine(
    var thresholdMs: Long = 20_000,
    var requiredCount: Int = 2,
    var requiredBandCount: Int = 3,
    val moveLimitMs: Long = 3_000,
    val lightOffDelayMs: Long = 10_000,
    val placeRadiusM: Double = 200.0,
    var helpWindowMs: Long = 60_000,
    val announceDelayMs: Long = 3_000,
    val doneMs: Long = 3_000,
    val pulseOnMs: Long = 400,
    val pulseOffMs: Long = 600,
    val rideBackrestMs: Long = 1_200,
    val exitBackrestMs: Long = 800,
    val exitStepMs: Long = 800,
    val boardBackrestMs: Long = 400,
    confirmed: Boolean = false,
    guided: Boolean = false,
    counts: Map<Seat, Int> = emptyMap(),
    places: List<Place> = emptyList(),
    rides: List<Ride> = emptyList(),
    private val capable: (Seat, Axis) -> Boolean = { _, _ -> true },
    private val cold: () -> Boolean = { false },
    private val dark: () -> Boolean = { false },
    private val postureMs: (Seat, Posture, Axis) -> Long? = { _, _, _ -> null },   // 부호 FORWARD +, null이면 기본
) {
    data class Loc(val lat: Double, val lon: Double)

    /** 한 자리에서 지금 진행 중인 이동 하나. pulsed면 on/off를 번갈아 낸다. */
    private class Motion(val axis: Axis, val dir: Move, val startedAt: Long, val limitMs: Long, val pulsed: Boolean, val restoring: Boolean, val ctx: SignalCtx, val label: String, val manual: Boolean = false) {
        var on = true
        var toggledAt = startedAt
        var doneMs = 0L                       // 끝난 펄스들의 이동 시간 합
        fun movedMs(now: Long) = doneMs + if (on) now - toggledAt else 0L
    }

    /** 예고 뒤 앉은 사람을 태운 채 할 이동 한 단계. */
    private class Step(val axis: Axis, val dir: Move, val ms: Long, val label: String)

    private class SeatState(var count: Int) {
        var doorOpen = false; var seated = false
        var doorOpenedAt: Long? = null; var seatedDuringOpen = false
        var motion: Motion? = null
        val moved = mutableMapOf<Axis, Long>()   // 축별로 FORWARD로 간 시간(+), BACKWARD(-)의 누적. 0이면 원위치
        fun moved(a: Axis) = moved[a] ?: 0L
        var lit = false                       // 독서등을 우리가 켬
        var fixedThisStop = false             // 이번 정차에서 문 열림/준비로 이미 움직였음. P 해제 시 리셋
        var hold = false                      // 탑승 확인 질문 답을 기다리는 중
        var rideDone = false; var exitDone = false
        var announceAt: Long? = null                        // 예고 시각. announceDelayMs 뒤 queue를 차례로 시작
        val queue = mutableListOf<Step>()                   // 예고 뒤 이어서 할 이동(앉은 사람, 펄스)
        var lightOffAt: Long? = null
        var ctx = SignalCtx.BOARD                 // 예고·큐의 맥락
        var done = mutableListOf<String>()        // 이번 자세에서 끝난 단계 이름
        var doneAt: Long? = null; var doneCtx = SignalCtx.BOARD; var doneSteps = emptyList<String>()
        var pending: SignalCtx? = null                      // P 해제 때 멈춘 것. 다음 P에서 이어감
        var heatOn = false; var heatRefused = false         // 열선: 우리가 켬 / 보호자가 꺼서 이 탑승엔 안 켬
        var beltKnown = false; var belted = false
        var restopAxis: Axis? = null                        // 주행 중 STOP이 거부된 축. 다음 P에서 STOP을 한 번 더 보냄
        var unseatedAt: Long? = null                        // 착좌 해제 시각(내릴 때 걸린 시간 기록용)
    }

    var heatEnabled = true

    private val seats = Seat.values().associateWith { SeatState(counts[it] ?: 0) }
    private var park: Boolean? = null
    private var drove = false                 // 이번 P 이전에 주행이 있었음
    private var driverSeated: Boolean? = null
    private var driverDoorOpen: Boolean? = null
    private var prepDone = false
    private var arrivalSaid = false
    var arrivedAt: Long? = null; private set
    /** SDK가 이동을 거부함(주행 중 제한 제외). 멈추고 남은 단계는 버린다. 멈춘 자리에서 타도 안전하므로 부모님께는 DONE. */
    fun onMoveFailed(seat: Seat, axis: Axis, now: Long): List<Command> {
        val s = seats.getValue(seat); val out = mutableListOf<Command>()
        if (s.motion?.axis != axis) return out
        val ctx = s.motion!!.ctx
        s.queue.clear(); stop(seat, s, out, "이동 실패", now)
        if (ctx != SignalCtx.RESTORE) { s.ctx = ctx; finish(s, out, now) }
        return out
    }
    private var here: Loc? = null
    /** 「부모님 타셨어요?」 질문 중인 자리. */
    var question: Seat? = null; private set
    private val placeList = places.toMutableList()
    val places: List<Place> get() = placeList
    private fun placeAt(l: Loc?) = l?.let { x -> placeList.firstOrNull { distanceM(Loc(it.lat, it.lon), x) <= placeRadiusM } }
    fun renamePlace(id: Int, name: String) { placeList.firstOrNull { it.id == id }?.name = name }
    fun removePlace(id: Int) { placeList.removeAll { it.id == id } }

    private fun slotOf(at: Long): Pair<Int, Int> = java.util.Calendar.getInstance().let { c -> c.timeInMillis = at; c.get(java.util.Calendar.DAY_OF_WEEK) to c.get(java.util.Calendar.HOUR_OF_DAY) * 2 + c.get(java.util.Calendar.MINUTE) / 30 }
    /** 느린(기준 초과 또는 도움) 탑승을 요일·30분 칸으로 묶고, 앞뒤 한 칸까지 세어 준다. */
    fun bands(): List<Band> {
        val slow = rideLog.filter { it.kind == RideKind.BOARD && (it.helped || it.seconds * 1000 >= thresholdMs) }.map { slotOf(it.at) }
        val all = slow.distinct().map { (w, s) -> Band(w, s, slow.count { it.first == w && Math.abs(it.second - s) <= 1 }) }
        // 이웃 칸(±1)과 겹치면 수가 많은 쪽, 같으면 이른 칸만 남긴다
        return all.filter { b -> all.none { o -> o.weekday == b.weekday && Math.abs(o.slot - b.slot) == 1 && (o.count > b.count || (o.count == b.count && o.slot < b.slot)) } }.sortedByDescending { it.count }
    }
    fun learnedBands() = bands().filter { it.count >= requiredBandCount }
    fun inBand(now: Long): Boolean? = learnedBands().ifEmpty { return null }.let { bs -> slotOf(now).let { (w, s) -> bs.any { it.weekday == w && Math.abs(it.slot - s) <= 1 } } }
    /** 가장 많이 탄 자리 + 가장 많은 학습된 시간대. 둘 중 하나라도 없으면 null. */
    fun prediction(): Pair<Seat, Band>? {
        val seat = rideLog.filter { it.kind == RideKind.BOARD }.groupingBy { it.seat }.eachCount().maxByOrNull { it.value }?.key ?: return null
        return learnedBands().firstOrNull()?.let { seat to it }
    }
    private val rideLog = rides.toMutableList()
    val rides: List<Ride> get() = rideLog
    private var driverLeftAt: Long? = null     // 보호자가 운전석을 비운 시각

    var isConfirmed = confirmed; private set
    var guided = guided; private set   // 확정 뒤 처음 문이 열릴 때 신호 설명을 했음
    var paused = false; private set
    var easyAccess = false   // 켜면 앞뒤(SLIDE)는 움직이지 않음
    fun setPaused(p: Boolean, now: Long): List<Command> {
        paused = p
        val out = mutableListOf<Command>(Command.Log(if (p) "오늘은 움직이지 않기" else "다시 움직임"))
        if (p) for ((seat, s) in seats) { s.announceAt = null; s.queue.clear(); stop(seat, s, out, "움직이지 않기", now) }
        return out
    }

    fun count(seat: Seat) = seats.getValue(seat).count
    val totalCount get() = seats.values.sumOf { it.count }
    val pendingConfirm get() = !isConfirmed && totalCount >= requiredCount
    fun isMoving(seat: Seat) = seats.getValue(seat).motion != null
    fun isRestoring(seat: Seat) = seats.getValue(seat).motion?.restoring == true
    fun isAnnouncing(seat: Seat) = seats.getValue(seat).announceAt != null
    fun isDoorOpen(seat: Seat) = seats.getValue(seat).doorOpen
    fun isSeated(seat: Seat) = seats.getValue(seat).seated
    val location: Loc? get() = here
    val countsSnapshot: Map<Seat, Int> get() = seats.mapValues { it.value.count }
    /** 앞뒤 이동 정도 -1..1 (+ 뒤로, - 앞으로). 이동 중이면 경과 비율. 화면 도식용. */
    fun slideProgress(seat: Seat, now: Long): Float = seats.getValue(seat).let { s ->
        val m = s.motion
        val base = (0f - s.moved(Axis.SLIDE).toFloat() / moveLimitMs).coerceIn(-1f, 1f)
        if (m == null || m.axis != Axis.SLIDE) return base
        val frac = (m.movedMs(now).toFloat() / moveLimitMs).coerceIn(0f, 1f)
        (base + if (m.dir == Move.BACKWARD) frac else -frac).coerceIn(-1f, 1f)
    }
    /** 등받이를 세운 정도 0..1. */
    fun backrestLevel(seat: Seat): Float = (seats.getValue(seat).moved(Axis.BACKREST).toFloat() / (rideBackrestMs + exitBackrestMs)).coerceIn(0f, 1f)
    /** 축의 이동 정도 -1..1 (FORWARD +). 이동 중이면 경과분 포함. */
    fun level(seat: Seat, axis: Axis, now: Long): Float = seats.getValue(seat).let { s ->
        val full = when (axis) { Axis.SLIDE -> moveLimitMs; Axis.BACKREST -> rideBackrestMs + exitBackrestMs; else -> exitStepMs }.toFloat()
        val m = s.motion?.takeIf { it.axis == axis }
        val run = if (m == null) 0L else if (m.dir == Move.FORWARD) m.movedMs(now) else -m.movedMs(now)
        ((s.moved(axis) + run) / full).coerceIn(-1f, 1f)
    }
    /** 자리들 중 하나의 신호. 우선순위 WAIT > SOON > DONE. 원위치 이동은 신호가 아니다. */
    fun signal(now: Long): Signal? {
        if (park != true) return null
        val all = seats.mapNotNull { (seat, s) ->
            val m = s.motion
            when {
                m != null && !m.restoring && !m.manual -> Signal(seat, m.ctx, SignalKind.WAIT, m.startedAt, s.done + m.label + s.queue.map { it.label }, s.done.size, null)
                s.announceAt != null -> Signal(seat, s.ctx, SignalKind.SOON, s.announceAt!!, s.queue.map { it.label }, 0, ((s.announceAt!! + announceDelayMs - now + 999) / 1000).toInt().coerceAtLeast(1))
                s.doneAt != null && now < s.doneAt!! + doneMs -> Signal(seat, s.doneCtx, SignalKind.DONE, s.doneAt!!, s.doneSteps, s.doneSteps.size, null)
                s.doorOpen && !s.seated && isConfirmed && isPrepared(seat) -> Signal(seat, SignalCtx.BOARD, SignalKind.DONE, s.doorOpenedAt ?: now, s.done.toList(), s.done.size, null)
                else -> null
            }
        }
        return all.minByOrNull { it.kind.ordinal.let { k -> if (k == 1) -1 else k } }   // WAIT(1) 먼저, 다음 SOON(0), DONE(2)
    }
    /** 주행 중 띠용. 멈춘 자세(RIDE/EXIT)가 원위치(RESTORE)보다 먼저. */
    fun pending(): Pending? = seats.entries.mapNotNull { (k, v) -> v.pending?.let { Pending(k, it) } }.minByOrNull { if (it.ctx == SignalCtx.RESTORE) 1 else 0 }
    /** 이번 정차에 넓혀 둔 빈 자리(탈 때 자세 준비됨). */
    fun isPrepared(seat: Seat) = seats.getValue(seat).let { it.fixedThisStop && it.motion == null && !it.seated && it.moved(Axis.SLIDE) != 0L }
    fun waitingSeconds(seat: Seat, now: Long): Long? = seats.getValue(seat).let { s -> s.doorOpenedAt?.takeIf { !s.seatedDuringOpen }?.let { (now - it) / 1000 } }

    fun status(seat: Seat): String = seats.getValue(seat).let { s ->
        val m = s.motion
        when {
            m?.restoring == true -> "원위치 중"
            m?.ctx == SignalCtx.BOARD -> "공간 확보 중"
            m?.axis == Axis.BACKREST -> "등받이 세우는 중"
            m?.pulsed == true -> "앉은 채 자리 맞추는 중"
            m != null -> "공간 확보 중"
            s.announceAt != null -> "자세 예고"
            s.seated && s.moved(Axis.BACKREST) > 0 -> "탑승 · 등받이 세움"
            s.seated -> "탑승"
            s.doorOpen && !s.seated -> "문 열림 · 착좌 대기"
            s.moved(Axis.SLIDE) != 0L -> "공간 확보됨"
            else -> if (isConfirmed) "대기" else "학습 ${s.count}회"
        }
    }

    fun onGear(park: Boolean, now: Long): List<Command> {
        if (this.park == park) return emptyList()
        val out = mutableListOf<Command>()
        if (!park) {
            drove = true; prepDone = false
            for ((seat, s) in seats) {
                // 멈추기 전에 무엇이 끊기는지 기록. 빈 자리는 무엇이 끊겼든 다음 P에서 원위치
                s.pending = when {
                    !s.seated -> if (s.motion != null || s.queue.isNotEmpty() || Axis.values().any { s.moved(it) != 0L }) SignalCtx.RESTORE else null
                    s.announceAt != null || s.queue.isNotEmpty() || s.motion?.pulsed == true -> s.ctx
                    else -> null
                }
                if (s.seated && s.pending != null) s.exitDone = false   // 끊긴 내릴 때 자세는 다음 P에서 다시 예고
                s.fixedThisStop = false; s.doneAt = null; cancelAnnounce(seat, s, out, "P 해제")
                // 주행 중에는 STOP도 거부(50001)되므로 움직이던 중에 끊긴 이동은 계획한 상한까지 간 것으로 본다. 펄스가 쉬는 동안(이미 STOP)이면 서 있으니 움직인 만큼만
                val m = s.motion; val movedSoFar = m?.movedMs(now)?.coerceIn(0L, m.limitMs) ?: 0L
                val moving = m != null && !m.manual && (m.on || !m.pulsed)
                stop(seat, s, out, "P 해제", now)
                if (moving) { s.moved[m!!.axis] = s.moved(m.axis) + (if (m.dir == Move.FORWARD) 1 else -1) * (m.limitMs - movedSoFar); s.restopAxis = m.axis }
            }
        }
        this.park = park
        if (park) for ((seat, s) in seats) s.restopAxis?.let { s.restopAxis = null; out += Command.MoveSeat(seat, it, Move.STOP) }
        if (park && drove) {
            arrivalSaid = false; arrivedAt = null
            for ((seat, s) in seats) if (s.seated && !s.exitDone && !s.hold && canMove(seat, Axis.BACKREST)) {
                s.exitDone = true
                // 등받이 → 높이 → 쿠션 앞 내림, 원위치에 있는 앞뒤는 다시 넓히는 쪽으로(앉은 사람 자신에게는 끼임 없음). 조수석 뒤로는 뒷자리가 비었을 때만
                val widen = s.moved(Axis.SLIDE) == 0L && canMove(seat, Axis.SLIDE) && (seat != Seat.PASSENGER || listOf(Seat.REAR_LEFT, Seat.REAR_RIGHT).none { seats.getValue(it).seated })
                val steps = listOf(step(seat, Posture.EXIT, Axis.BACKREST, Move.FORWARD, exitBackrestMs), step(seat, Posture.EXIT, Axis.HEIGHT, Move.FORWARD, exitStepMs), step(seat, Posture.EXIT, Axis.TILT, Move.BACKWARD, exitStepMs)) +
                    if (widen) listOf(Step(Axis.SLIDE, Move.BACKWARD, moveLimitMs, "뒤로")) else emptyList()
                announce(seat, s, out, now, steps, "등받이를 세울게요. 문은 '딩동' 뒤에 열어 주세요.", SignalCtx.EXIT)
            }
            for ((seat, s) in seats) if (s.pending != null) {
                s.pending = null
                if (!s.seated) maybeRestore(seat, s, out, now)   // 빈 자리는 예고 없이 원위치
            }
        }
        return out
    }

    fun onDriverSeated(seated: Boolean, now: Long): List<Command> {
        if (driverSeated == seated) return emptyList()
        driverSeated = seated; if (!seated) driverLeftAt = now
        return guardianChanged(now)
    }

    fun onDriverDoor(open: Boolean, now: Long): List<Command> {
        if (driverDoorOpen == open) return emptyList()
        driverDoorOpen = open
        return guardianChanged(now)
    }

    fun onDoor(seat: Seat, open: Boolean, now: Long): List<Command> {
        val s = seats.getValue(seat)
        if (s.doorOpen == open) return emptyList()
        s.doorOpen = open
        val out = mutableListOf<Command>()
        if (open) {
            s.doorOpenedAt = now; s.seatedDuringOpen = s.seated; s.heatRefused = false   // 새 탑승
            s.motion?.let { if (!it.restoring && !it.manual) out += Command.Say("잠시만 기다려 주세요.") }
            if (s.ctx != SignalCtx.BOARD) cancelAnnounce(seat, s, out, "문 열림")       // 타기 준비(BOARD)는 넓히는 쪽이라 계속(9.5)
            s.motion?.let { if (it.restoring || it.ctx != SignalCtx.BOARD) stop(seat, s, out, "문 열림", now) }
            if (isConfirmed) {
                s.lightOffAt = null; light(seat, s, out, true)
                if (!guided && isPrepared(seat)) { guided = true; out += Command.Say("'띵, 띵' 하는 동안은 기다리시고, '딩동' 하고 초록 불이 켜지면 타세요.") }
            }
            if (park == true && isConfirmed && !paused) prepare(seat, s, out, now, "${seat.label} 문 열림")
        } else {
            if (isConfirmed) s.unseatedAt?.let { rideLog += Ride(now, seat, (now - it) / 1000, false, null, RideKind.EXIT) }
            s.unseatedAt = null
            if (s.doorOpenedAt != null && !s.seatedDuringOpen && !isConfirmed) out += Command.Log("${seat.label} 착좌 없음, 짐 싣기로 제외")
            s.doorOpenedAt = null
            stop(seat, s, out, "문 닫힘", now); s.queue.clear()
            if (isConfirmed) s.lightOffAt = now + lightOffDelayMs
            maybeRide(seat, s, out, now)
            val n = out.size
            maybeRestore(seat, s, out, now)
            if (out.drop(n).any { it is Command.MoveSeat }) out += Command.Say("자리를 원래대로 돌릴게요.")
        }
        return out
    }

    fun onSeated(seat: Seat, seated: Boolean, now: Long): List<Command> {
        val s = seats.getValue(seat)
        if (s.seated == seated) return emptyList()
        s.seated = seated
        val out = mutableListOf<Command>()
        if (seated) {
            s.seatedDuringOpen = true; s.doneAt = null; s.unseatedAt = null
            stop(seat, s, out, "착좌 감지", now); s.queue.clear()               // 남은 준비 단계도 하지 않음
            s.doorOpenedAt?.let { openedAt ->
                val helped = driverSeated == false && driverLeftAt?.let { now - it <= helpWindowMs } == true
                val slow = now - openedAt >= thresholdMs || helped
                val place = if (slow) placeAt(here) ?: here?.let { val id = (placeList.maxOfOrNull { p -> p.id } ?: 0) + 1; Place(id, it.lat, it.lon, "알아본 곳 $id", 0, 0, 0).also { p -> placeList += p; out += Command.Log("새 장소 「${p.name}」 (${"%.4f".format(p.lat)}, ${"%.4f".format(p.lon)})") } } else placeAt(here)
                if (slow && place != null) { place.count++; if (helped) place.helpedCount++; place.lastAt = now }
                rideLog += Ride(now, seat, (now - openedAt) / 1000, helped, place?.id, RideKind.BOARD)
                if (!isConfirmed && slow) learn(seat, s, out, now, openedAt, helped)
                if (isConfirmed && !slow) {
                    val known = placeAt(here) != null || inBand(now) == true
                    if (!known) { question = seat; s.hold = true; out += Command.Log("${seat.label} 빨리 앉음, 알아본 곳·시간대 아님 → 부모님인지 물음"); out += Command.Say("부모님 타셨어요? 화면에서 알려 주세요.") }
                }
            }
            if (isConfirmed && heatEnabled && !s.heatRefused && cold()) { s.heatOn = true; out += Command.SeatHeat(seat, 2); out += Command.Log("${seat.label} 추움 → 열선 2단") }
            maybeRide(seat, s, out, now)
        } else {
            if (s.heatOn) { s.heatOn = false; out += Command.SeatHeat(seat, 0) }
            s.unseatedAt = now
            s.hold = false; if (question == seat) question = null
            cancelAnnounce(seat, s, out, "착좌 해제")
            if (s.motion?.pulsed == true) stop(seat, s, out, "착좌 해제", now)
            s.rideDone = false; s.exitDone = false
            maybeRestore(seat, s, out, now)
        }
        return out
    }

    /** 자세 맞추기 화면의 버튼. 누르는 동안 움직이고 STOP으로 멈춘다. 누적되어 원위치 대상이 된다. */
    fun manual(seat: Seat, axis: Axis, move: Move, now: Long): List<Command> {
        val s = seats.getValue(seat); val out = mutableListOf<Command>()
        if (move == Move.STOP) { if (s.motion?.manual == true) stop(seat, s, out, "수동 멈춤", now); return out }
        if (park != true || !capable(seat, axis) || s.motion != null) return out
        start(seat, s, out, Motion(axis, move, now, moveLimitMs, pulsed = false, restoring = false, ctx = SignalCtx.RESTORE, label = label(axis, move), manual = true), "수동")
        return out
    }
    fun clearAll() { isConfirmed = false; paused = false; question = null; seats.values.forEach { it.count = 0; it.hold = false }; rideLog.clear(); placeList.clear() }

    fun confirm(): List<Command> { isConfirmed = true; return listOf(Command.Log("부모님으로 기억"), Command.Say("기억할게요. 다음부터 타고 내리실 때 자리를 맞춰 드릴게요.")) }
    fun reject(): List<Command> { seats.values.forEach { it.count = 0 }; placeList.clear(); return listOf(Command.Log("아니요 → 처음부터 다시 셈")) }
    fun unconfirm(): List<Command> { isConfirmed = false; seats.values.forEach { it.count = 0 }; placeList.clear(); return listOf(Command.Log("기억 해제")) }

    /** 현재 위치. 값이 같아도 1Hz로 오므로 저장만. */
    fun onLocation(lat: Double, lon: Double) { here = Loc(lat, lon) }

    /** 기억한 장소 지오펜스 진입. 주행 중이고 부모님 자리가 차 있으면 한 번 안내. */
    fun onArrive(now: Long): List<Command> {
        if (park != false || !isConfirmed || arrivalSaid || seats.values.none { it.seated }) return emptyList()
        arrivalSaid = true; arrivedAt = now
        return listOf(Command.Log("기억한 장소 근처 → 도착 전 안내"), Command.Say("곧 도착해요. 내리기 편하게 자리를 바꿀게요."))
    }

    /** 시간 이벤트. 250ms~1s마다 불러 준다. */
    fun tick(now: Long): List<Command> {
        val out = mutableListOf<Command>()
        for ((seat, s) in seats) {
            s.announceAt?.let { at -> if (now >= at + announceDelayMs) { s.announceAt = null; startNext(seat, s, out, now) } }
            s.motion?.let { m ->
                if (m.movedMs(now) >= m.limitMs) {
                    stop(seat, s, out, if (m.restoring) "원위치 완료" else "시간 상한", now)
                    if (m.restoring) maybeRestore(seat, s, out, now) else if (m.pulsed) startNext(seat, s, out, now)
                }
                else if (m.pulsed && m.on && now - m.toggledAt >= pulseOnMs) { m.doneMs += now - m.toggledAt; m.on = false; m.toggledAt = now; out += Command.MoveSeat(seat, m.axis, Move.STOP) }
                else if (m.pulsed && !m.on && now - m.toggledAt >= pulseOffMs) { m.on = true; m.toggledAt = now; out += Command.MoveSeat(seat, m.axis, m.dir) }
            }
            s.lightOffAt?.let { if (now >= it && park == true && !s.doorOpen) { s.lightOffAt = null; light(seat, s, out, false) } }   // 주행 중 조명 명령은 거부됨 → P까지 미룸. 문이 열린 동안은 끄지 않음
        }
        return out
    }

    // ---- 내부 ----

    private fun canMove(seat: Seat, axis: Axis) = park == true && isConfirmed && !paused && !(easyAccess && axis == Axis.SLIDE) && capable(seat, axis)

    /** 보호자 하차 → 지난번 탄 자리 준비. 보호자 복귀 → 비어 있는 자리 원위치. */
    private fun guardianChanged(now: Long): List<Command> {
        val out = mutableListOf<Command>()
        if (driverSeated == true) prepDone = false   // 돌아왔다가 다시 내리면 또 준비
        if (park == true && driverSeated == false && driverDoorOpen == true && !prepDone && isConfirmed && !paused) {
            prepDone = true
            val seat = rideLog.lastOrNull { it.kind == RideKind.BOARD }?.seat   // 탄 기록이 없으면 어느 자리인지 몰라 문이 열릴 때 함
            if (seat != null && !seats.getValue(seat).doorOpen && prepare(seat, seats.getValue(seat), out, now, "보호자 하차 → 지난번 자리")) out += Command.Say("잠시만 기다려 주세요.")
        }
        if (driverSeated == true && driverDoorOpen == false) {
            val n = out.size
            for ((seat, s) in seats) maybeRestore(seat, s, out, now)
            if (out.drop(n).any { it is Command.MoveSeat }) out += Command.Say("자리를 원래대로 돌릴게요.")
        }
        return out
    }

    /** 빈 자리를 타기 쉽게 한 번만: 앞뒤 뒤로, 이어서 등받이 조금 뒤로(펄스). 움직였으면 true. */
    private fun prepare(seat: Seat, s: SeatState, out: MutableList<Command>, now: Long, why: String): Boolean {
        if (s.seated || s.fixedThisStop || s.motion != null || s.moved(Axis.SLIDE) != 0L) return false
        val steps = listOf(step(seat, Posture.BOARD, Axis.SLIDE, Move.BACKWARD, moveLimitMs), step(seat, Posture.BOARD, Axis.BACKREST, Move.BACKWARD, boardBackrestMs)).filter { canMove(seat, it.axis) }
        if (steps.isEmpty()) return false
        s.fixedThisStop = true; s.ctx = SignalCtx.BOARD; s.done.clear(); s.doneAt = null
        s.queue.clear(); s.queue += steps
        out += Command.Log("$why → ${seat.label} ${steps.joinToString("→") { it.label }}")
        startNext(seat, s, out, now)
        return true
    }

    private fun learn(seat: Seat, s: SeatState, out: MutableList<Command>, now: Long, openedAt: Long, helped: Boolean) {
        val sec = (now - openedAt) / 1000
        when {
            now - openedAt < thresholdMs && !helped -> out += Command.Log("${seat.label} 앉기까지 ${sec}초, 기준 이하")
            else -> {
                s.count++
                out += Command.Log("${seat.label} 앉기까지 ${sec}초${if (helped) ", 보호자님 도움" else ", 기준 초과"} → 학습 $totalCount/$requiredCount")
                if (totalCount == requiredCount) {
                    val front = count(Seat.PASSENGER); val rear = count(Seat.REAR_LEFT) + count(Seat.REAR_RIGHT)
                    out += Command.Say("부모님이 타실 때 오래 걸렸어요. 조수석 ${front}번, 뒷자리 ${rear}번. 부모님으로 기억할까요? 화면에서 알려 주세요.")
                }
            }
        }
    }

    /** 팝업 답. 예면 바로 가는 동안 자세, 아니요면 이번 정차는 그대로. */
    fun answerBoarding(yes: Boolean, now: Long): List<Command> {
        val seat = question ?: return emptyList()
        question = null
        val s = seats.getValue(seat); s.hold = false
        val out = mutableListOf<Command>(Command.Log(if (yes) "부모님 탑승 확인" else "부모님 아님 → 이번 정차는 그대로"))
        if (yes) maybeRide(seat, s, out, now) else { s.rideDone = true; s.exitDone = true }
        return out
    }

    /** SDK가 알려준 열선 단계. 우리가 켠 뒤 0이 되면 보호자가 끈 것 → 이 탑승 동안 다시 켜지 않음. */
    fun onHeat(seat: Seat, level: Int): List<Command> = seats.getValue(seat).let { s ->
        if (s.heatOn && level == 0) { s.heatOn = false; s.heatRefused = true; listOf(Command.Log("${seat.label} 열선 꺼짐 → 이번 탑승엔 다시 켜지 않음")) } else emptyList()
    }

    /** 벨트. 실차는 자리별 리스너, 에뮬레이터는 Activity가 벨트 경고등 변화를 부모님 자리로 넘긴다. */
    fun onBelt(seat: Seat, buckled: Boolean, now: Long): List<Command> {
        val s = seats.getValue(seat); val out = mutableListOf<Command>()
        if (s.beltKnown && s.belted == buckled) return out
        s.beltKnown = true; s.belted = buckled
        if (buckled) maybeRide(seat, s, out, now)
        else if (s.seated && s.exitDone && park == true) out += Command.Say("다리를 하나씩 내놓고, 두 발이 땅에 닿은 뒤 일어나세요.")
        return out
    }

    /** 착좌 + 문 닫힘 + P → 가는 동안 자세 예고. */
    private fun maybeRide(seat: Seat, s: SeatState, out: MutableList<Command>, now: Long) {
        if (!s.seated || s.doorOpen || s.rideDone || s.hold || s.motion != null || (s.beltKnown && !s.belted) || !canMove(seat, Axis.BACKREST)) return
        s.rideDone = true
        // 등받이를 세우고, 타느라 물러난 앞뒤를 원래 자리로(PRD F14 「시트 조금 당김」). 뒤로 빼 둔 채 달리지 않는다
        val back = s.moved(Axis.SLIDE) != 0L && canMove(seat, Axis.SLIDE)
        val slideDir = if (s.moved(Axis.SLIDE) < 0) Move.FORWARD else Move.BACKWARD
        val steps = listOf(step(seat, Posture.RIDE, Axis.BACKREST, Move.FORWARD, rideBackrestMs)) + if (back) listOf(Step(Axis.SLIDE, slideDir, Math.abs(s.moved(Axis.SLIDE)), label(Axis.SLIDE, slideDir))) else emptyList()
        announce(seat, s, out, now, steps, "등받이를 세울게요. 출발은 '딩동' 뒤에 해 주세요.", SignalCtx.RIDE)
    }

    private fun announce(seat: Seat, s: SeatState, out: MutableList<Command>, now: Long, allSteps: List<Step>, text: String, ctx: SignalCtx) {
        val steps = allSteps.filter { canMove(seat, it.axis) }
        s.ctx = ctx; s.done.clear(); s.doneAt = null
        s.announceAt = now; s.queue.clear(); s.queue += steps; s.lightOffAt = null
        out += Command.Log("${seat.label} 자세 예고 (${announceDelayMs / 1000}초 뒤 ${steps.joinToString("→") { it.axis.name.lowercase() }})")
        out += Command.Say(text)
        light(seat, s, out, true)
    }

    /** 예고가 끝났거나 한 단계가 끝났을 때 다음 단계를 펄스로 시작한다. 남은 게 없으면 독서등을 늦게 끈다. */
    private fun startNext(seat: Seat, s: SeatState, out: MutableList<Command>, now: Long) {
        while (s.queue.isNotEmpty()) {
            val st = s.queue.removeAt(0)
            if (st.ms <= 0 || !canMove(seat, st.axis)) continue
            start(seat, s, out, Motion(st.axis, st.dir, now, st.ms, pulsed = true, restoring = false, ctx = s.ctx, label = st.label), "자세")
            return
        }
        finish(s, out, now)
        s.lightOffAt = now + lightOffDelayMs
    }

    /** 자세가 끝남 → doneMs 동안 DONE 신호, 문장 하나. */
    private fun finish(s: SeatState, out: MutableList<Command>, now: Long) {
        s.doneAt = now; s.doneCtx = s.ctx; s.doneSteps = s.done.toList()
        out += Command.Say(when (s.doneCtx) { SignalCtx.BOARD -> "이제 타셔도 돼요. 머리 조심하세요."; SignalCtx.RIDE -> "다 됐어요. 이제 출발하셔도 돼요."; SignalCtx.EXIT -> "다 됐어요. 문을 열어 드릴 때까지 앉아 계세요."; SignalCtx.RESTORE -> return })
    }

    /** 자세 단계 하나. 람다 값이 있으면 그 절대값·부호(FORWARD +)로, 없으면 기본. */
    private fun step(seat: Seat, p: Posture, axis: Axis, defDir: Move, defMs: Long): Step {
        val v = postureMs(seat, p, axis) ?: return Step(axis, defDir, defMs, label(axis, defDir))
        val dir = if (v >= 0) Move.FORWARD else Move.BACKWARD
        return Step(axis, dir, Math.abs(v), label(axis, dir))
    }

    private fun label(axis: Axis, dir: Move) = when (axis) { Axis.SLIDE -> if (dir == Move.BACKWARD) "뒤로" else "앞으로"; else -> axis.label }

    /** 독서등. 켜기는 어두울 때(dark)만, 끄기는 우리가 켰을 때만. */
    private fun light(seat: Seat, s: SeatState, out: MutableList<Command>, on: Boolean) {
        if (on == s.lit || (on && !dark())) return
        s.lit = on; out += Command.ReadingLight(seat, on)
    }

    private fun cancelAnnounce(seat: Seat, s: SeatState, out: MutableList<Command>, why: String) {
        if (s.announceAt == null && s.queue.isEmpty()) return
        s.announceAt = null; s.queue.clear(); s.doneAt = null
        out += Command.Log("${seat.label} $why → 자세 이동 취소")
    }

    private fun start(seat: Seat, s: SeatState, out: MutableList<Command>, m: Motion, why: String) {
        if (s.motion != null) stop(seat, s, out, "교체", m.startedAt)
        s.motion = m
        out += Command.MoveSeat(seat, m.axis, m.dir)
    }

    /** 진행 중 이동을 멈추고 실제로 움직인 시간을 누적한다. */
    private fun stop(seat: Seat, s: SeatState, out: MutableList<Command>, why: String, now: Long) {
        val m = s.motion ?: return
        s.motion = null
        val moved = m.movedMs(now).coerceIn(0L, m.limitMs)
        val sign = if (m.dir == Move.FORWARD) 1 else -1
        s.moved[m.axis] = s.moved(m.axis) + sign * moved
        if (Axis.values().all { s.moved(it) == 0L }) s.fixedThisStop = false   // 원래 자리로 돌아오면 이번 정차에 다시 준비할 수 있음
        if (m.pulsed && s.queue.isEmpty()) s.lightOffAt = now + lightOffDelayMs
        if (m.pulsed) s.done += m.label
        out += Command.Log("${seat.label} $why → 정지 (${m.axis.name.lowercase()} ${moved}ms)")
        if (m.on || !m.pulsed) out += Command.MoveSeat(seat, m.axis, Move.STOP)
    }

    /** 비어 있고 문 닫힌 자리를 원래대로. 보호자가 운전석에 앉아 문을 닫았을 때(P)만. 축 순서(앞뒤→등받이→높이→기울기)대로 한 축씩. */
    private fun maybeRestore(seat: Seat, s: SeatState, out: MutableList<Command>, now: Long) {
        if (park != true || s.seated || s.doorOpen || s.motion != null || paused) return
        if (!(driverSeated == true && driverDoorOpen == false)) return
        val axis = Axis.values().firstOrNull { s.moved(it) != 0L } ?: return
        val v = s.moved(axis)
        if (!capable(seat, axis)) { s.moved[axis] = 0; maybeRestore(seat, s, out, now); return }
        val dir = if (v > 0) Move.BACKWARD else Move.FORWARD
        val m = Motion(axis, dir, now, Math.abs(v), pulsed = false, restoring = true, ctx = SignalCtx.RESTORE, label = label(axis, dir))
        out += Command.Log("${seat.label} 원위치 (${m.axis.name.lowercase()} ${m.limitMs}ms)")
        start(seat, s, out, m, "원위치")
    }


    companion object {
        fun distanceM(a: Loc, b: Loc): Double {   // haversine
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
            val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2).let { it * it }
            return 2 * r * Math.asin(Math.sqrt(h))
        }
    }
}
