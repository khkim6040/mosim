package dev.gwanho.pleossample

import java.util.Calendar

data class SeatUi(val seated: Boolean, val doorOpen: Boolean, val moving: Boolean, val prepared: Boolean, val status: String, val lightOn: Boolean, val heat: Int, val levels: Map<Axis, Float>)
data class Settings(val thresholdMs: Long, val requiredCount: Int, val requiredBandCount: Int, val helpWindowMs: Long, val easyAccess: Boolean, val heatEnabled: Boolean, val volume: Int, val paused: Boolean)
/** 내비에서 온 목적지 정보(띠 18 글자). */
data class Navi(val remainSec: Int, val remainM: Int, val name: String, val eta: String)
/** 「300m」「8.4km」 */
fun distText(m: Int) = if (m < 1000) "${m}m" else String.format(java.util.Locale.US, "%.1fkm", m / 1000.0)

sealed class Screen {
    object Home : Screen(); object Cards : Screen(); object Settings : Screen(); object Mobility : Screen()
    data class Places(val id: Int?) : Screen()
    data class PostureEdit(val seat: Seat, val posture: Posture) : Screen()
}

/** Activity가 250ms마다 만드는 불변 스냅샷. Compose는 이것만 본다. */
data class UiState(
    val now: Long, val park: Boolean, val started: Boolean, val confirmed: Boolean, val pendingConfirm: Boolean, val driverSeated: Boolean?,
    val question: Seat?, val failure: String?, val signal: Signal?, val pending: Pending?, val arrivalUntil: Long, val navi: Navi?,
    val rides: List<Ride>, val places: List<Place>, val bands: List<Band>, val prediction: Pair<Seat, Band>?, val requiredCount: Int, val totalCount: Int,
    val seats: Map<Seat, SeatUi>, val caps: Map<Pair<Seat, Axis>, Boolean>, val lightCap: Boolean, val ambientCap: Boolean, val heatCap: Set<Seat>, val memory: Map<Seat, IntRange>,
    val settings: Settings, val invert: Boolean, val logs: List<String>, val screens: List<Screen>, val mobilityDismissed: Boolean, val pressed: Map<Axis, Long>,
)

sealed class UiEvent {
    object Start : UiEvent(); object Confirm : UiEvent(); object Reject : UiEvent(); object Unconfirm : UiEvent()
    data class Pause(val on: Boolean) : UiEvent(); data class EasyAccess(val on: Boolean) : UiEvent()
    data class Push(val s: Screen) : UiEvent(); object Pop : UiEvent(); object PlaySounds : UiEvent(); object StopAll : UiEvent()
    data class Answer(val yes: Boolean) : UiEvent(); object DismissFailure : UiEvent()
    data class SetThreshold(val ms: Long) : UiEvent(); data class SetRequired(val n: Int) : UiEvent(); data class SetHelpWindow(val ms: Long) : UiEvent()
    data class Heat(val on: Boolean) : UiEvent(); data class Volume(val v: Int) : UiEvent(); object ClearAll : UiEvent(); data class Invert(val on: Boolean) : UiEvent()
    data class RenamePlace(val id: Int, val name: String) : UiEvent(); data class RemovePlace(val id: Int) : UiEvent(); object OpenNavi : UiEvent()
    data class Manual(val seat: Seat, val axis: Axis, val move: Move) : UiEvent(); data class SavePosture(val seat: Seat, val posture: Posture) : UiEvent()
    data class Memory(val seat: Seat, val slot: Int) : UiEvent(); object MobilityDismiss : UiEvent()
}

private val WD = arrayOf("", "일", "월", "화", "수", "목", "금", "토")
/** 「매주 화요일 오후 2시 반」 */
fun bandText(b: Band): String { val h = b.slot / 2; return "매주 ${WD[b.weekday]}요일 ${if (h < 12) "오전" else "오후"} ${(h % 12).let { if (it == 0) 12 else it }}시${if (b.slot % 2 == 1) " 반" else ""}" }
/** 「화요일 오후」 */
fun bandShort(b: Band): String = "${WD[b.weekday]}요일 ${if (b.slot / 2 < 12) "오전" else "오후"}"
/** 「11/3 (화)」 */
fun dateText(at: Long): String = Calendar.getInstance().let { c -> c.timeInMillis = at; "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)} (${WD[c.get(Calendar.DAY_OF_WEEK)]})" }
/** 「집 앞 34초(보호자님 도움)」 */
fun rideLine(r: Ride, places: List<Place>): String = "${places.firstOrNull { it.id == r.placeId }?.name ?: "어디선가"} ${r.seconds}초${if (r.helped) "(보호자님 도움)" else ""}"

/** 이동 변화에 쓰는 자리: 예상 자리, 없으면 가장 많이 탄 자리. */
fun mobilitySeat(s: UiState): Seat = s.prediction?.first ?: s.rides.groupingBy { it.seat }.eachCount().maxByOrNull { it.value }?.key ?: Seat.PASSENGER
/** (이번 달, 지난달) 통계. 달력 기준: 이번 달 = now의 연월, 지난달 = 그 바로 앞 달. 없으면 null. */
fun mobilityCompare(m: List<MonthStat>, now: Long): Pair<MonthStat?, MonthStat?> {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val cur = c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
    c.add(Calendar.MONTH, -1)
    val prev = c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
    return m.firstOrNull { it.ym == cur } to m.firstOrNull { it.ym == prev }
}
data class MonthStat(val ym: Int, val label: String, val board: Int?, val exit: Int?, val count: Int)
/** 자리 하나의 달별 평균(초). 최근 3달, 기록 없는 달은 비움(목록에 없음). count = 그 달 내릴 때 기록 수. */
fun mobility(rides: List<Ride>, seat: Seat, now: Long): List<MonthStat> {
    fun ym(at: Long) = Calendar.getInstance().let { it.timeInMillis = at; it.get(Calendar.YEAR) * 100 + it.get(Calendar.MONTH) + 1 }
    val mine = rides.filter { it.seat == seat }.groupBy { ym(it.at) }
    return mine.keys.sorted().takeLast(3).map { k ->
        val b = mine.getValue(k).filter { it.kind == RideKind.BOARD }.map { it.seconds }; val x = mine.getValue(k).filter { it.kind == RideKind.EXIT }.map { it.seconds }
        MonthStat(k, "${k % 100}월", b.takeIf { it.isNotEmpty() }?.average()?.toInt(), x.takeIf { it.isNotEmpty() }?.average()?.toInt(), x.size)
    }
}
