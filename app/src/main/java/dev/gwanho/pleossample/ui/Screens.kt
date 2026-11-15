package dev.gwanho.pleossample.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.gwanho.pleossample.*

/** 01 처음 실행 (Figma 14:36). */
@Composable
fun Onboarding(s: UiState, on: (UiEvent) -> Unit) = Canvas1700 {
    T(48, 45, "모심 시작하기", 48, 700)
    T(48, 150, "부모님 자리를 차가 맞춰 드려요", 56, 700)
    T(48, 230, "부모님이 타고 내릴 때마다 시트를 미리 맞추고, 소리와 불빛으로 알려 드려요.", 30, 400, C.text2)
    R(48, 310, 780, 640, C.card, 32)
    T(88, 340, "켜 두기만 하면 차가 알아봐요", 36, 700); T(88, 392, "등록할 것은 없어요.", 26, 400, C.text2)
    listOf(Triple(464, "타실 때마다 재요", "문이 열리고 앉으실 때까지 걸린 시간, 보호자님이 내려 도와 드렸는지"),
        Triple(586, "되풀이되면 한 번 여쭤봐요", "같은 곳이나 같은 시간대에 오래 걸리면 「부모님으로 기억할까요?」"),
        Triple(708, "그다음부터 미리 맞춰요", "매주 한 번 모시면 3주째부터 운전석에 앉기만 해도 준비돼요")).forEachIndexed { i, (y, t, d) ->
        R(88, y, 56, 56, C.dark, 28) { Text("${i + 1}", Modifier.align(Alignment.Center), color = C.white, style = ts(28, 700)) }
        T(168, y - 8, t, 30, 700); T(168, y + 36, d, 24, 400, C.text2, w = 620)
    }
    R(88, 819, 700, 2, C.line, 1)
    T(88, 840, "이지 액세스를 쓰고 계세요?", 30, 700); T(88, 884, "앞뒤는 차에 맡기고 등받이·높이만 맞춰요", 24, 400, C.text2)
    Toggle(708, 856, s.settings.easyAccess) { on(UiEvent.EasyAccess(!s.settings.easyAccess)) }
    R(868, 310, 784, 170, C.cream, 32, C.amber to 3)
    WarnIcon(898, 349)
    T(990, 334, "부모님을 모실 때는 시동을 켜 두세요", 32, 700); T(990, 382, "시동이 꺼지면 앱도 꺼져서 미리 맞출 수 없어요.", 26, 400, C.text2)
    R(868, 504, 784, 300, C.card, 32)
    T(908, 528, "이 차에서 도울 수 있는 것", 32, 700)
    CapChips(908, 588, 744, s.caps, s.lightCap, s.ambientCap, Seat.PASSENGER in s.heatCap)
    R(868, 828, 784, 152, C.card, 32)
    T(908, 848, "부모님께 보여 드릴 신호 카드", 32, 700)
    R(908, 902, 156, 56, C.amber, 16) { Text("곧 움직여요", Modifier.align(Alignment.Center), color = C.ink, style = ts(22, 700)) }
    R(1076, 902, 156, 56, C.red, 16) { Text("기다려 주세요", Modifier.align(Alignment.Center), color = C.white, style = ts(22, 700)) }
    R(1244, 902, 156, 56, C.green, 16) { Text("됐어요", Modifier.align(Alignment.Center), color = C.white, style = ts(22, 700)) }
    Btn(1442, 890, 170, 80, "크게 보기", size = 28) { on(UiEvent.Push(Screen.Cards)) }
    T(48, 1060, "알아본 곳과 기록은 설정에서 언제든 지울 수 있어요.", 26, 400, C.text3)
    Btn(1292, 1036, 360, 100, "시작하기", primary = true) { on(UiEvent.Start) }
}

/** 17 신호 카드 (14:173). */
@Composable
fun Cards(s: UiState, on: (UiEvent) -> Unit) = Canvas1700 {
    Back { on(UiEvent.Pop) }
    T(140, 45, "부모님께 보여 드리세요", 48, 700)
    T(48, 130, "차가 시트를 움직일 때 이 세 가지 신호로 알려 드려요.", 30, 400, C.text2)
    Card17(48, C.amber, C.ink, "곧 움직여요", "기대거나 잡지 마세요", "「등받이를 세울게요」", "실내 호박색 불") {
        Box(Modifier.at(12, 12, 156, 156).border(8.dp, C.ink, CircleShape)); Text("3", Modifier.align(Alignment.Center), color = C.ink, style = ts(96, 700))
    }
    Card17(593, C.red, C.white, "기다려 주세요", "타지 말고 기다려 주세요", "띵 · 띵 (1초마다)", "빨간 불 깜빡임 · 독서등 꺼짐") {
        Box(Modifier.at(60, 52, 20, 76).background(C.white)); Box(Modifier.at(100, 52, 20, 76).background(C.white))
    }
    Card17(1139, C.green, C.white, "됐어요", "이제 타셔도, 내리셔도 돼요", "딩동", "초록 불 · 독서등 켜짐") {
        CheckIcon(76, 58, C.white, 14f, Modifier.at(54, 60))
    }
    Btn(48, 1036, 360, 100, "소리 들려 드리기") { on(UiEvent.PlaySounds) }
    Btn(1412, 1036, 240, 100, "닫기", primary = true) { on(UiEvent.Pop) }
}
@Composable
private fun Card17(x: Int, bg: Color, fg: Color, title: String, sub: String, sound: String, light: String, icon: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    R(x, 200, 513, 800, bg, 40)
    R(x + 167, 250, 180, 180, fg.copy(alpha = 0.16f), 90, content = icon)
    T(x, 484, title, 56, 800, fg, w = 513, align = TextAlign.Center)
    T(x, 570, sub, 30, 700, fg, w = 513, align = TextAlign.Center)
    R(x + 32, 680, 449, 116, C.white.copy(alpha = 0.18f), 24); T(x + 64, 702, "소리", 24, 700, fg.copy(alpha = 0.85f)); T(x + 64, 742, sound, 28, 700, fg)
    R(x + 32, 820, 449, 116, C.white.copy(alpha = 0.18f), 24); T(x + 64, 842, "불빛", 24, 700, fg.copy(alpha = 0.85f)); T(x + 64, 882, light, 28, 700, fg)
}

/** 02 홈·알아보는 중 (14:277) / 04 홈·부모님 (14:563). 확정 여부로 갈린다. */
@Composable
fun Home(s: UiState, on: (UiEvent) -> Unit) = Canvas1700 {
    T(48, 45, "모심", 48, 700)
    if (s.confirmed) R(163, 48, 118, 56, C.mint, 28) { Text("부모님", Modifier.align(Alignment.Center), color = C.greenText, style = ts(26, 600)) }
    Btn(1532, 44, 120, 64, "설정", size = 26) { on(UiEvent.Push(Screen.Settings)) }
    val p = s.prediction
    if (!s.confirmed) {
        R(48, 136, 1604, 176, C.info, 32)
        R(88, 188, 72, 72, C.blue, 36) { Text("i", Modifier.align(Alignment.Center), color = C.white, style = ts(40, 700)) }
        T(188, 160, "부모님을 알아보는 중이에요 · ${s.totalCount}/${s.requiredCount}", 36, 700)
        T(188, 216, "같은 곳이나 같은 시간대에 타실 때 오래 걸리거나 보호자님이 내려 도와 드린 일이 한 번 더 있으면 여쭤볼게요. 등록할 것은 없어요.", 26, 400, C.text2, w = 1400)
    } else {
        R(48, 136, 1604, 176, C.mint, 32)
        ClockIcon(97, 197, 54, C.greenText)
        T(188, 160, if (p != null) "${bandShort(p.second)} · ${p.first.label}에 타실 것 같아요" else "부모님을 기억하고 있어요", 36, 700)
        T(188, 216, if (p != null) "매주 이맘때 ${s.places.firstOrNull()?.name ?: "같은 곳"}에서 타셨어요. 보호자님이 운전석에 앉거나 모시러 내리시면 ${p.first.label}을 미리 맞춰 둘게요."
            else "타고 내리실 때 자리를 맞춰 드릴게요. 보호자님이 운전석에서 내리시면 지난번에 타신 자리를 미리 맞춰 둘게요.", 26, 400, C.text2, w = 1150)
        if (p != null) R(1502, 196, 93, 56, C.white, 28) { Text("예상", Modifier.align(Alignment.Center), style = ts(26, 600)) }
    }
    R(48, 344, 800, 792, C.card, 32); T(88, 368, "좌석", 32, 700)
    SeatMap(Seat.values().associateWith { seat ->
        val u = s.seats.getValue(seat)
        val (sub, sub2) = when { u.prepared -> "탈 때 자세" to "준비됨"; u.moving -> "움직이는 중" to null; u.seated -> "탑승" to (if (u.lightOn) "독서등" else if (u.heat > 0) "열선" else null); else -> "그대로" to null }
        SeatTile(when (seat) { Seat.PASSENGER -> "조수석"; Seat.REAR_LEFT -> "왼쪽"; Seat.REAR_RIGHT -> "오른쪽" }, sub, sub2,
            highlight = s.confirmed && (seat == p?.first || u.prepared),
            onLongClick = if (s.confirmed) ({ on(UiEvent.Push(Screen.PostureEdit(seat, Posture.BOARD))) }) else null)
    }, s.driverSeated, if (s.confirmed) "좌석을 길게 누르면 자세 맞추기" else "프로필이 생기기 전에는 시트를 움직이지 않아요")
    R(888, 344, 764, 220, C.card, 32, onClick = { on(UiEvent.Push(Screen.Places(null))) })
    PinIcon(936, 421, 49, 65, C.blue)
    if (!s.confirmed) {
        val slow = s.rides.filter { it.kind == RideKind.BOARD && (it.helped || it.seconds * 1000 >= s.settings.thresholdMs) }
        T(1030, 404, "기록한 탑승 ${slow.size}번", 36, 700)
        T(1030, 462, slow.takeLast(2).joinToString(" · ") { rideLine(it, s.places) }.ifEmpty { "아직 없어요" }, 26, 400, C.text2, w = 540)
    } else {
        T(1030, 404, "알아본 곳 ${s.places.size} · 시간대 ${s.bands.count { it.count >= s.settings.requiredBandCount }}", 36, 700)
        T(1030, 462, listOfNotNull(s.places.joinToString(", ") { it.name }.ifEmpty { null }, p?.let { bandText(it.second) }).joinToString(" · ").ifEmpty { "아직 없어요" }, 26, 400, C.text2, w = 540)
    }
    Chevron(1599, 443, 11, 22, C.text3, 4f, left = false)
    R(888, 588, 764, 128, C.card, 32); T(928, 610, "오늘은 움직이지 않기", 32, 700); T(928, 656, "내일 아침에 다시 켜져요", 24, 400, C.text2)
    Toggle(1532, 628, s.settings.paused) { on(UiEvent.Pause(!s.settings.paused)) }
    R(888, 740, 764, 396, C.card, 32); T(928, 768, "이 차에서 도울 수 있는 것", 32, 700)
    CapChips(928, 830, 724, s.caps, s.lightCap, s.ambientCap, Seat.PASSENGER in s.heatCap)
    T(928, 1060, "앱이 켜질 때 차의 기능을 확인해 되는 것만 해요", 24, 400, C.text3)
}

/** 15 설정 (16:1088) + 「이동 변화 보기」 행 + 「개발자 로그」 접기(설계서 7절 6·9항). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(s: UiState, on: (UiEvent) -> Unit) = Canvas1700 {
    Back { on(UiEvent.Pop) }; T(140, 45, "설정", 48, 700)
    R(48, 136, 780, 1000, C.card, 32); T(88, 164, "알아보기 기준", 30, 700, C.text2)
    Stepper(244, "오래 걸림 기준", "${s.settings.thresholdMs / 1000}초", { on(UiEvent.SetThreshold((s.settings.thresholdMs - 5_000).coerceAtLeast(5_000))) }, { on(UiEvent.SetThreshold((s.settings.thresholdMs + 5_000).coerceAtMost(120_000))) })
    R(88, 367, 700, 2, C.line, 1)
    Stepper(394, "학습 횟수", "장소 ${s.settings.requiredCount}번 · 시간대 ${s.settings.requiredBandCount}번", { on(UiEvent.SetRequired((s.settings.requiredCount - 1).coerceAtLeast(1))) }, { on(UiEvent.SetRequired((s.settings.requiredCount + 1).coerceAtMost(5))) })
    R(88, 517, 700, 2, C.line, 1)
    Stepper(544, "보호자 도움 간격", "${s.settings.helpWindowMs / 1000}초", { on(UiEvent.SetHelpWindow((s.settings.helpWindowMs - 15_000).coerceAtLeast(15_000))) }, { on(UiEvent.SetHelpWindow((s.settings.helpWindowMs + 15_000).coerceAtMost(300_000))) })
    R(88, 667, 700, 2, C.line, 1)
    T(88, 694, "알아본 곳·시간", 30, 700)
    T(88, 742, listOfNotNull(s.places.joinToString(", ") { it.name }.ifEmpty { null }, s.prediction?.let { bandShort(it.second) }).joinToString(" · ").ifEmpty { "아직 없어요" }, 26, 400, C.text2, w = 540)
    Btn(638, 702, 150, 80, "보기", size = 28) { on(UiEvent.Push(Screen.Places(null))) }
    T(88, 860, "처음 값은 근거 없이 정한 값이에요. 실제로 재 보고 고쳐요.", 24, 400, C.text3)
    var dev by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    R(88, 910, 700, 2, C.line, 1)
    R(88, 924, 700, 48, Color.Transparent, onClick = { dev = !dev }) { T(0, 4, "개발자 로그", 30, 700); Chevron(680, 14, 11, 22, C.text3, 4f, left = false) }
    if (dev) {
        T(88, 984, "착좌 극성 반전(에뮬레이터)", 24, 400, C.text2); Toggle(708, 976, s.invert) { on(UiEvent.Invert(!s.invert)) }
        R(88, 1032, 700, 96, C.white, 16, C.line to 2) { Text(s.logs.takeLast(4).joinToString("\n"), Modifier.padding(12.dp), color = C.text2, style = ts(16, 400), maxLines = 4) }
    }
    R(872, 136, 780, 1000, C.card, 32); T(912, 164, "움직임과 신호", 30, 700, C.text2)
    T(912, 244, "이지 액세스 사용 중", 30, 700); T(912, 292, "켜면 앞뒤는 차에 맡기고 등받이·높이만", 24, 400, C.text2); Toggle(1532, 268, s.settings.easyAccess) { on(UiEvent.EasyAccess(!s.settings.easyAccess)) }
    R(912, 367, 700, 2, C.line, 1)
    T(912, 394, "추우면 열선 켜기", 30, 700); T(912, 442, "실내 온도로 판단해요", 24, 400, C.text2); Toggle(1532, 418, s.settings.heatEnabled) { on(UiEvent.Heat(!s.settings.heatEnabled)) }
    R(912, 517, 700, 2, C.line, 1)
    T(912, 544, "신호 소리 크기", 30, 700); T(912, 592, "띵 · 딩동 · 안내 말", 24, 400, C.text2)
    androidx.compose.material3.Slider(value = s.settings.volume / 100f, onValueChange = { on(UiEvent.Volume((it * 100).toInt())) }, modifier = Modifier.at(1340, 560, 284, 64),
        colors = androidx.compose.material3.SliderDefaults.colors(activeTrackColor = C.blue, inactiveTrackColor = C.line),
        thumb = { Box(Modifier.size(44.dp).background(C.white, CircleShape).border(4.dp, C.blue, CircleShape)) })
    R(912, 667, 700, 2, C.line, 1)
    T(912, 694, "부모님께 보여 드릴 신호 카드", 30, 700); T(912, 742, "세 가지 신호의 소리와 불빛", 24, 400, C.text2); Btn(1482, 702, 130, 80, "보기", size = 28) { on(UiEvent.Push(Screen.Cards)) }
    R(912, 817, 700, 2, C.line, 1)
    T(912, 844, "기록 지우기", 30, 700, C.danger); T(912, 892, "프로필, 탑승 기록, 알아본 곳", 24, 400, C.text2); Btn(1462, 852, 150, 80, "지우기", size = 28, textColor = C.danger) { on(UiEvent.ClearAll) }
    R(912, 967, 700, 2, C.line, 1)
    T(912, 994, "이동 변화 보기", 30, 700); T(912, 1042, "달별 평균 · 타실 때와 내리실 때", 24, 400, C.text2); Btn(1482, 1002, 130, 80, "보기", size = 28) { on(UiEvent.Push(Screen.Mobility)) }
}
@Composable
private fun Stepper(y: Int, title: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    T(88, y, title, 30, 700); T(88, y + 48, value, 26, 400, C.blue)
    Btn(612, y + 8, 80, 80, "−", size = 36, onClick = minus); Btn(708, y + 8, 80, 80, "+", size = 36, onClick = plus)
}

/** 13 알아본 곳과 시간 (16:1204). 왼쪽 장소·시간대 목록, 오른쪽 고른 장소의 격자 지도·이름·기록·규칙. */
@Composable
fun PlacesScreen(s: UiState, sel: Int?, on: (UiEvent) -> Unit) = Canvas1700 {
    Back { on(UiEvent.Pop) }; T(140, 45, "알아본 곳과 시간", 48, 700)
    R(48, 136, 760, 1000, C.card, 32); T(88, 160, "장소", 28, 700, C.text2)
    val cur = s.places.firstOrNull { it.id == sel } ?: s.places.firstOrNull()
    val shown = s.places.sortedByDescending { it.count }.take(5)   // 많이 탄 순 5곳
    shown.forEachIndexed { i, p ->
        val y = 204 + i * 140; val picked = p.id == cur?.id
        R(64, y, 728, 132, if (picked) C.white else Color.Transparent, 24, if (picked) C.blue to 4 else null, onClick = { on(UiEvent.Push(Screen.Places(p.id))) })
        PinIcon(102, y + 39, 36, 49, if (picked) C.blue else C.text2)
        T(168, y + 16, p.name, 32, 700)
        T(168, y + 66, "오래 걸린 탑승 ${p.count}번 · " + (if (p.helpedCount > 0) "보호자님 도움 ${p.helpedCount}번" else "마지막 ${dateText(p.lastAt)}"), 24, 400, C.text2)
        Chevron(755, y + 55, 11, 22, C.text3, 4f, left = false)
    }
    val by = 204 + shown.size * 140 + 22
    T(88, by, "시간대", 28, 700, C.text2)
    val learned = s.bands.filter { it.count >= s.settings.requiredBandCount }
    learned.forEachIndexed { i, b ->
        val y = by + 64 + i * 150
        ClockIcon(100, y + 28, 40, C.text2); T(168, y, bandText(b), 32, 700); T(168, y + 50, "앞뒤 30분 · 오래 걸린 탑승 ${b.count}번", 24, 400, C.text2); Chevron(755, y + 39, 11, 22, C.text3, 4f, left = false)
    }
    if (learned.isEmpty()) T(168, by + 64, "아직 없어요", 26, 400, C.text3)
    val ly = by + 64 + maxOf(1, learned.size) * 150 - 20
    R(88, ly, 680, 2, C.line, 1)
    T(88, ly + 25, "보호자님이 등록하지 않아요. 같은 곳이나 같은 시간대에 오래 걸린 탑승이 되풀이되면 차가 알아봐요.", 24, 400, C.text3, w = 680)
    R(848, 136, 804, 1000, C.card, 32)
    R(888, 176, 724, 360, C.map, 24, onClick = { on(UiEvent.OpenNavi) }) {
        Canvas(Modifier.fillMaxSize()) {
            for (yy in listOf(44, 114, 184, 254, 324)) drawLine(C.mapLine, Offset(0f, yy.dp.toPx()), Offset(size.width, yy.dp.toPx()), 14.dp.toPx())
            for (xx in listOf(252, 492)) drawLine(C.mapLine, Offset(xx.dp.toPx(), 0f), Offset(xx.dp.toPx(), size.height), 14.dp.toPx())
        }
        if (cur != null) { Box(Modifier.at(232, 50, 260, 260).background(C.blue.copy(alpha = 0.12f), CircleShape).border(3.dp, C.blue, CircleShape)); PinIcon(335, 128, 53, 71, C.blue) }
    }
    if (cur == null) { T(888, 560, "아직 알아본 곳이 없어요", 30, 700, C.text3); return@Canvas1700 }
    T(888, 560, "이름 (바꿀 수 있어요)", 26, 700, C.text2)
    var name by androidx.compose.runtime.remember(cur.id) { androidx.compose.runtime.mutableStateOf(cur.name) }
    R(888, 600, 724, 100, C.white, 24, C.line to 3) { androidx.compose.foundation.text.BasicTextField(name, { name = it }, Modifier.at(32, 30, 660, 40), textStyle = ts(30, 400), singleLine = true) }
    T(888, 724, "기록", 26, 700, C.text2)
    val here = s.rides.filter { it.placeId == cur.id && it.kind == RideKind.BOARD }
    T(888, 766, (here.takeLast(3).map { "${dateText(it.at).substringBefore(" ")} ${it.seconds}초" } + "반경 ${200}m").joinToString(" · "), 24, 400, C.text2, w = 724)
    R(888, 850, 724, 176, C.white, 24, C.line to 3)
    T(920, 870, "이렇게 알아봐요", 26, 700); T(920, 922, "이곳에서 타실 때", 28, 400)
    R(1138, 916, 118, 52, C.cream, 26) { Text("그리고", Modifier.align(Alignment.Center), color = C.amberText, style = ts(26, 600)) }
    T(920, 972, "${s.settings.thresholdMs / 1000}초 넘게 걸리거나 보호자님이 도와 드리면 → 부모님", 28, 400)
    Btn(888, 1040, 240, 80, "이곳 지우기", size = 28, textColor = C.danger) { on(UiEvent.RemovePlace(cur.id)); on(UiEvent.Pop) }
    Btn(1412, 1040, 200, 80, "저장", primary = true, size = 28) { on(UiEvent.RenamePlace(cur.id, name.trim().ifEmpty { cur.name })); on(UiEvent.Pop) }
}

/** 12 자세 맞추기 (13:3). 축 버튼은 누르는 동안만 움직인다. capability 없는 축·P 아님은 흐리게. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PostureScreen(s: UiState, seat: Seat, posture: Posture, on: (UiEvent) -> Unit) = Canvas1700 {
    Back { on(UiEvent.Pop) }; T(140, 45, "자세 맞추기", 48, 700)
    Btn(1392, 44, 260, 64, "이대로 기억", primary = true, size = 26) { on(UiEvent.SavePosture(seat, posture)) }
    R(48, 136, 880, 100, C.bg, 32); Seat.values().forEachIndexed { i, st -> val sel = st == seat
        R(56 + i * 292, 144, 288, 84, if (sel) C.white else Color.Transparent, 24, onClick = { on(UiEvent.Pop); on(UiEvent.Push(Screen.PostureEdit(st, posture))) }) { Text(st.label, Modifier.align(Alignment.Center), color = if (sel) C.ink else C.text2, style = ts(30, if (sel) 700 else 400)) } }
    R(952, 136, 700, 100, C.bg, 32); Posture.values().forEachIndexed { i, p -> val sel = p == posture
        R(960 + i * 232, 144, 228, 84, if (sel) C.white else Color.Transparent, 24, onClick = { on(UiEvent.Pop); on(UiEvent.Push(Screen.PostureEdit(seat, p))) }) { Text(p.label, Modifier.align(Alignment.Center), color = if (sel) C.ink else C.text2, style = ts(30, if (sel) 700 else 400)) } }
    R(48, 268, 760, 868, C.card, 32); T(88, 292, "${seat.label} · ${posture.label} 자세", 32, 700)
    Box(Modifier.at(158, 403, 512, 501).graphicsLayer { scaleX = 2.5f; scaleY = 2.5f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }) { SeatLine(0, 0, C.text2, 3.2f) }
    Canvas(Modifier.at(158, 403, 512, 501)) {
        val d = density
        drawLine(C.blue, Offset(146 * d, 464 * d), Offset(359 * d, 464 * d), 18 * d, StrokeCap.Round)      // 앞뒤
        drawLine(C.blue, Offset(484 * d, 324 * d), Offset(484 * d, 444 * d), 18 * d, StrokeCap.Round)      // 높이
        drawArc(C.blue, 200f, 70f, false, Offset(0f, 27 * d), androidx.compose.ui.geometry.Size(292 * d, 208 * d), style = Stroke(18 * d, cap = StrokeCap.Round))   // 등받이
    }
    T(412, 925, "앞뒤", 26, 700, C.blue); T(662, 628, "높이", 26, 700, C.blue); T(365, 390, "등받이", 26, 700, C.blue)
    T(88, 1000, "P에서만 움직여요. 버튼을 누르는 동안만 움직여요.", 26, 400, C.text2)
    T(88, 1044, "이번에 누른 시간: " + s.pressed.filter { it.value != 0L }.entries.joinToString(" · ") { "${it.key.label} ${"%+.1f".format(it.value / 1000.0)}초" }.ifEmpty { "없음" } + " · 이동 시간으로 기억해요", 24, 400, C.text3, w = 680)
    val rows = listOf(
        Triple(Axis.SLIDE, "타느라 물러난 시트는 뒤에 사람이 있을 때만 되돌려요", listOf("앞으로" to Move.FORWARD, "뒤로" to Move.BACKWARD)),
        Triple(Axis.BACKREST, "가는 동안은 부모님이 편한 각도로 세워요", listOf("세우기" to Move.FORWARD, "젖히기" to Move.BACKWARD)),
        Triple(Axis.HEIGHT, "내릴 때는 높여서 일어서기 쉽게 해요", listOf("내리기" to Move.BACKWARD, "올리기" to Move.FORWARD)),
        Triple(Axis.TILT, "내릴 때는 쿠션 앞쪽을 낮춰 엉덩이를 들어 줘요", listOf("앞 내리기" to Move.BACKWARD, "앞 올리기" to Move.FORWARD)))
    rows.forEachIndexed { i, (axis, desc, btns) ->
        val y = 268 + i * 172; val enabled = s.park && s.caps[seat to axis] == true && !(s.settings.easyAccess && axis == Axis.SLIDE)
        R(848, y, 804, 156, C.card, 32); T(888, y + 24, axis.label, 32, 700); T(888, y + 74, desc, 24, 400, C.text2, w = 350)
        btns.forEachIndexed { j, (label, mv) ->
            Box(Modifier.at(1256 + j * 186, y + 38, 170, 80).background(C.bg, RoundedCornerShape(24.dp)).graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                .then(if (enabled) Modifier.pointerInput(seat, axis, mv) { detectTapGestures(onPress = { on(UiEvent.Manual(seat, axis, mv)); try { tryAwaitRelease() } finally { on(UiEvent.Manual(seat, axis, Move.STOP)) } }) } else Modifier)) {
                Text(label, Modifier.align(Alignment.Center), style = ts(28, 700))
            }
        }
    }
    R(848, 960, 804, 176, C.card, 32); T(888, 980, "차에 저장한 메모리 불러오기", 30, 700); T(888, 1024, "메모리 시트가 있는 차만", 24, 400, C.text3)
    for (slot in 1..3) { val ok = s.memory[seat]?.contains(slot) == true
        Btn(888 + (slot - 1) * 112, 1056, 96, 64, "$slot", primary = ok, size = 26) { if (ok) on(UiEvent.Memory(seat, slot)) } }
}

/** 14 이동 변화 (16:979). 막대 = 달별 평균 초(최대 40초로 자름). */
@Composable
fun MobilityScreen(s: UiState, on: (UiEvent) -> Unit) = Canvas1700 {
    val seat = mobilitySeat(s); val m = mobility(s.rides, seat, s.now); val (last, prev) = mobilityCompare(m, s.now)
    val diff = if (last?.exit != null && prev?.exit != null) last.exit - prev.exit else null
    Back { on(UiEvent.Pop) }; T(140, 45, "부모님 이동 변화", 48, 700)
    R(48, 136, 1604, 168, C.cream, 32); R(48, 136, 14, 168, C.amber, 7)
    T(96, 160, when { diff == null -> "비교할 지난달 기록이 없어요"; diff > 0 -> "지난달보다 내리실 때 평균 ${diff}초 더 걸렸어요"; diff < 0 -> "지난달보다 내리실 때 평균 ${-diff}초 덜 걸렸어요"; else -> "지난달과 내리실 때 걸린 시간이 같아요" }, 40, 700)
    T(96, 222, if (diff != null && diff > 0) "불편한 데가 없으신지 여쭤 보세요." else "기록이 더 쌓이면 달별로 비교해 드려요.", 30, 400, C.text2)
    R(48, 332, 1040, 804, C.card, 32); T(88, 356, "달별 평균 (${seat.label}, 미리 맞춘 탑승만)", 30, 700)
    R(88, 420, 32, 32, C.bar, 8); T(132, 422, "타실 때", 24, 400, C.text2); R(288, 420, 32, 32, C.dark, 8); T(332, 422, "내리실 때", 24, 400, C.text2)
    for (i in 0..4) { R(150, 999 - i * 120, 890, 2, C.line, 1); T(40, 986 - i * 120, "${i * 10}초", 22, 400, C.text3, w = 90, align = TextAlign.End) }
    m.forEachIndexed { i, st ->
        val gx = 240 + i * 280
        st.board?.let { v -> val h = (v.coerceAtMost(40) * 12); R(gx, 1000 - h, 80, h, C.bar, 12); T(gx - 10, 1000 - h - 38, "${v}초", 24, 700, C.text2, w = 100, align = TextAlign.Center) }
        st.exit?.let { v -> val h = (v.coerceAtMost(40) * 12); R(gx + 96, 1000 - h, 80, h, if (i == m.lastIndex && (diff ?: 0) > 0) C.amber else C.dark, 12); T(gx + 86, 1000 - h - 38, "${v}초", 24, 700, w = 100, align = TextAlign.Center) }
        T(gx + 18, 1018, st.label, 26, 700, C.text2, w = 100, align = TextAlign.Center)
    }
    R(1112, 332, 540, 640, C.card, 32)
    T(1148, 360, "비교 조건", 28, 700); T(1148, 402, "같은 자리, 미리 맞춘 탑승끼리 · 한 달 8번 이상", 24, 400, C.text2, w = 470)
    T(1148, 494, "이번 달", 28, 700); T(1148, 536, if (last != null) "탑승 ${last.count}번 · 내리실 때 ${last.exit ?: 0}초${prev?.exit?.let { " (지난달 ${it}초)" } ?: ""}" else "기록 없음", 24, 400, C.text2, w = 470)
    T(1148, 596, "참고 정보예요", 28, 700); T(1148, 638, "진단이 아니에요. 걱정되시면 부모님께 여쭤 보세요.", 24, 400, C.text2, w = 470)
    T(1148, 730, "기록", 28, 700); T(1148, 772, "이 차에만 있어요. 설정에서 언제든 지울 수 있어요.", 24, 400, C.text2, w = 470)
    Btn(1112, 1036, 250, 100, "다시 보지 않기") { on(UiEvent.MobilityDismiss) }
    Btn(1402, 1036, 250, 100, "확인", primary = true) { on(UiEvent.MobilityDismiss) }
}
