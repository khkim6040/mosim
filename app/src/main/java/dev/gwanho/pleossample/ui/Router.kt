package dev.gwanho.pleossample.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.gwanho.pleossample.RideKind
import dev.gwanho.pleossample.Seat
import dev.gwanho.pleossample.distText
import dev.gwanho.pleossample.mobility
import dev.gwanho.pleossample.mobilityCompare
import dev.gwanho.pleossample.mobilitySeat
import dev.gwanho.pleossample.Screen
import dev.gwanho.pleossample.SignalCtx
import dev.gwanho.pleossample.UiEvent
import dev.gwanho.pleossample.UiState
import dev.gwanho.pleossample.dateText

/** 설계서 3절 표. 위에서부터 우선. Task 12(전체화면)·13(띠·팝업)·14·15(설정류)가 분기를 더한다. */
@Composable
fun Router(s: UiState, on: (UiEvent) -> Unit) = Box(Modifier.fillMaxSize()) {
    val top = s.screens.lastOrNull() ?: Screen.Home
    if (!s.park) { BandLayer(s); return@Box }
    if (!s.started && top != Screen.Cards) { Onboarding(s, on); return@Box }   // 시작 전에도 「크게 보기」(17)는 열린다
    s.signal?.let { if (it.ctx != SignalCtx.RESTORE) { FullSignal(it, on, s.seats.getValue(it.seat).lightOn); return@Box } }
    if (s.screens.isEmpty() && !s.mobilityDismissed && s.confirmed) {
        val seat = mobilitySeat(s); val (last, prev) = mobilityCompare(mobility(s.rides, seat, s.now), s.now)
        if (last != null && prev?.exit != null && last.exit != null && last.count >= 8 && last.exit - prev.exit >= 5 && !s.seats.getValue(seat).seated) { MobilityScreen(s, on); return@Box }
    }
    when (top) {
        Screen.Cards -> Cards(s, on)
        Screen.Settings -> SettingsScreen(s, on)
        is Screen.Places -> PlacesScreen(s, top.id, on)
        is Screen.PostureEdit -> PostureScreen(s, top.seat, top.posture, on)
        Screen.Mobility -> MobilityScreen(s, on)
        else -> Home(s, on)
    }
    when {
        s.failure != null -> Canvas1700(Color.Transparent) { Dialog(400, 384, "시트를 다 맞추지 못했어요", s.failure, listOf(Triple("확인", true) { on(UiEvent.DismissFailure) })) }
        s.question != null -> Canvas1700(Color.Transparent) { Dialog(400, 384, "부모님 타셨어요?", "${s.question.label}에 누가 타셨어요. 부모님이시면 가는 동안\n자세로 맞춰 드릴게요.",
            listOf(Triple("아니요", false) { on(UiEvent.Answer(false)) }, Triple("예, 부모님이에요", true) { on(UiEvent.Answer(true)) })) }
        s.pendingConfirm && s.driverSeated != false -> Canvas1700(Color.Transparent) {
            val slow = s.rides.filter { it.kind == RideKind.BOARD && (it.helped || it.seconds * 1000 >= s.settings.thresholdMs) }.takeLast(3)
            val where = s.places.firstOrNull()?.name ?: "타실 때"
            Dialog(313, 558, "부모님으로 기억할까요?", "${where}에서 타실 때 ${slow.size}번 모두 오래 걸렸어요. 기억하면\n다음부터 미리 자리를 맞춰 드려요.",
                listOf(Triple("아니요", false) { on(UiEvent.Reject) }, Triple("기억하기", true) { on(UiEvent.Confirm) })) {
                R(494, 533, 712, 150, C.card, 24)
                slow.forEachIndexed { i, r -> T(526, 559 + i * 56, "${dateText(r.at)}  ${s.places.firstOrNull { it.id == r.placeId }?.name ?: "어디선가"} · ${r.seat.label} · ${r.seconds}초${if (r.helped) " (보호자님 도움)" else ""}", 26, 400) }
            }
        }
    }
}

/** 기어≠P: 회색 격자 + 띠만(설계서 3절). 띠가 없으면 격자만. */
@Composable
private fun BandLayer(s: UiState) = Canvas1700(C.map) {
    MapGrid()
    val p = s.pending
    when {
        s.now < s.arrivalUntil -> Band(listOfNotNull("곧 도착해요 · 도착하면 내리기 편하게 자리를 바꿀게요", s.navi?.let { "${it.remainSec / 60}분 · ${distText(it.remainM)} · ${it.name} 도착 예정 ${it.eta}" }).joinToString("   "), "말로도 알려 드려요", null, sound = true)
        p != null && p.ctx != SignalCtx.RESTORE -> Band("출발해서 자리 맞추기를 멈췄어요 · 다음에 멈추면 이어서 맞출게요", "멈춤", 0.0f, sound = false)
        p != null -> Band("다음에 멈추면 빈 ${p.seat.label}을 원래대로 돌릴게요", "대기 중", 0.0f, sound = false)
    }
    s.navi?.let { R(48, 986, 560, 150, C.white, 32); T(88, 1006, "${it.remainSec / 60}분 · ${distText(it.remainM)}", 40, 700); T(88, 1066, "${it.name} 도착 예정 ${it.eta}", 26, 400, C.text2) }
}
