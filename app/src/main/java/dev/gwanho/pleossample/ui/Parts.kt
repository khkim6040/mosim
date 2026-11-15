package dev.gwanho.pleossample.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gwanho.pleossample.Axis
import dev.gwanho.pleossample.Seat
import dev.gwanho.pleossample.SignalCtx
import dev.gwanho.pleossample.SignalKind

/** 체크(✓)·엑스 아이콘이 붙은 56 높이 칩. ok=false면 회색 글자와 ✗. */
@Composable
fun Chip(text: String, ok: Boolean = true, bg: Color = C.bg) = Row(Modifier.height(56.dp).background(bg, RoundedCornerShape(28.dp)).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) {
    if (ok) CheckIcon(18, 14, C.greenText, 4f) else XIcon(14, C.text3)
    Spacer(Modifier.width(13.dp))
    Text(text, color = if (ok) C.ink else C.text3, style = ts(26, 600))
}

/** 「이 차에서 도울 수 있는 것」 칩 묶음. 순서·이름은 Figma. caps 값으로 ✓/✗. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapChips(x: Int, y: Int, w: Int, caps: Map<Pair<Seat, Axis>, Boolean>, lightCap: Boolean, ambientCap: Boolean, heatCap: Boolean) {
    fun ok(a: Axis) = caps[Seat.PASSENGER to a] == true
    val items = listOf("조수석 앞뒤" to ok(Axis.SLIDE), "등받이" to ok(Axis.BACKREST), "높이" to ok(Axis.HEIGHT), "쿠션 기울기" to ok(Axis.TILT),
        "독서등" to lightCap, "실내 조명 색" to ambientCap, "열선" to heatCap, "2열 앞뒤" to (caps[Seat.REAR_LEFT to Axis.SLIDE] == true))
    FlowRow(Modifier.at(x, y).width(w.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (t, o) -> Chip(t, o) }
    }
}

/** 80×48 토글. */
@Composable
fun Toggle(x: Int, y: Int, on: Boolean, onClick: () -> Unit) = R(x, y, 80, 48, if (on) C.toggle else C.knob, 16, onClick = onClick) {
    Box(Modifier.at(if (on) 38 else 6, 6, 36, 36).background(C.white, CircleShape))
}

/** 버튼. primary = 진한 바탕 흰 글. */
@Composable
fun Btn(x: Int, y: Int, w: Int, h: Int, text: String, primary: Boolean = false, size: Int = 32, textColor: Color? = null, onClick: () -> Unit) =
    R(x, y, w, h, if (primary) C.dark else C.bg, if (h >= 100) 32 else 24, onClick = onClick) {
        Text(text, Modifier.align(Alignment.Center), color = textColor ?: if (primary) C.white else C.ink, style = ts(size, 700))
    }

/** 왼쪽 위 뒤로 버튼(40,36) 80×80. */
@Composable
fun Back(onClick: () -> Unit) = R(40, 36, 80, 80, C.bg, 20, onClick = onClick) { Chevron(33, 25, 15, 30, C.ink, 6f, left = true) }

@Composable fun Chevron(x: Int, y: Int, w: Int, h: Int, color: Color, stroke: Float, left: Boolean) = Canvas(Modifier.at(x, y, w, h)) {
    val p = Path(); if (left) { p.moveTo(size.width, 0f); p.lineTo(0f, size.height / 2); p.lineTo(size.width, size.height) } else { p.moveTo(0f, 0f); p.lineTo(size.width, size.height / 2); p.lineTo(0f, size.height) }
    drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round))
}
@Composable fun CheckIcon(w: Int, h: Int, color: Color, stroke: Float, modifier: Modifier = Modifier) = Canvas(modifier.size(w.dp, h.dp)) {
    val p = Path(); p.moveTo(0f, size.height * 0.55f); p.lineTo(size.width * 0.38f, size.height); p.lineTo(size.width, 0f)
    drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round))
}
@Composable fun XIcon(s: Int, color: Color) = Canvas(Modifier.size(s.dp)) {
    drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), 4f, StrokeCap.Round); drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), 4f, StrokeCap.Round)
}
@Composable fun PauseIcon(x: Int, y: Int, color: Color) = Canvas(Modifier.at(x, y, 72, 91)) {
    drawRoundRect(color, Offset(0f, 0f), androidx.compose.ui.geometry.Size(24.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(6f))
    drawRoundRect(color, Offset(48.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(24.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(6f))
}
@Composable fun StopIcon(x: Int, y: Int, s: Int, color: Color) = Box(Modifier.at(x, y, s, s).background(color, RoundedCornerShape(4.dp)))
@Composable fun SoundIcon(x: Int, y: Int, color: Color, scale: Float = 1f) = Canvas(Modifier.at(x, y, (32 * scale).toInt(), (27 * scale).toInt())) {
    val p = Path(); p.moveTo(0f, size.height * 0.3f); p.lineTo(size.width * 0.25f, size.height * 0.3f); p.lineTo(size.width * 0.5f, 0f); p.lineTo(size.width * 0.5f, size.height); p.lineTo(size.width * 0.25f, size.height * 0.7f); p.lineTo(0f, size.height * 0.7f); p.close()
    drawPath(p, color)
    drawArc(color, -40f, 80f, false, Offset(size.width * 0.45f, size.height * 0.25f), androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height * 0.5f), style = Stroke(3f * scale, cap = StrokeCap.Round))
    drawArc(color, -45f, 90f, false, Offset(size.width * 0.5f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.5f, size.height), style = Stroke(3f * scale, cap = StrokeCap.Round))
}
@Composable fun WheelIcon(x: Int, y: Int, color: Color) = Canvas(Modifier.at(x, y, 88, 88)) {
    drawCircle(color, size.width / 2 - 3.5f, style = Stroke(7f)); drawCircle(color, 12.5f * density)
    drawLine(color, Offset(0f, size.height / 2), Offset(size.width * 0.36f, size.height / 2), 7f, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.64f, size.height / 2), Offset(size.width, size.height / 2), 7f, StrokeCap.Round)
    drawLine(color, Offset(size.width / 2, size.height * 0.64f), Offset(size.width / 2, size.height), 7f, StrokeCap.Round)
}
@Composable fun PinIcon(x: Int, y: Int, w: Int, h: Int, color: Color) = Canvas(Modifier.at(x, y, w, h)) {
    val p = Path(); p.moveTo(size.width / 2, size.height); p.lineTo(size.width * 0.05f, size.height * 0.45f); p.arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.width), 180f, 180f, false); p.close()
    drawPath(p, color); drawCircle(C.white, size.width * 0.18f, Offset(size.width / 2, size.width / 2))
}
@Composable fun ClockIcon(x: Int, y: Int, s: Int, color: Color) = Canvas(Modifier.at(x, y, s, s)) {
    drawCircle(color, size.width / 2 - 3f, style = Stroke(size.width * 0.107f))
    drawLine(color, Offset(size.width / 2, size.height * 0.22f), Offset(size.width / 2, size.height / 2), size.width * 0.107f, StrokeCap.Round)
    drawLine(color, Offset(size.width / 2, size.height / 2), Offset(size.width * 0.72f, size.height * 0.6f), size.width * 0.107f, StrokeCap.Round)
}
@Composable fun LightIcon(x: Int, y: Int, color: Color) = Canvas(Modifier.at(x, y, 49, 49)) {
    drawCircle(color, 11.dp.toPx()); for (i in 0 until 8) { val a = Math.toRadians(i * 45.0); val c = size.width / 2
        drawLine(color, Offset(c + (18.dp.toPx() * Math.cos(a)).toFloat(), c + (18.dp.toPx() * Math.sin(a)).toFloat()), Offset(c + (24.dp.toPx() * Math.cos(a)).toFloat(), c + (24.dp.toPx() * Math.sin(a)).toFloat()), 3f, StrokeCap.Round) }
}

/** 옆에서 본 시트 선화. (x, y)는 Figma 「시트 그림」 그룹 왼쪽 위(05: 744,652). 등받이·방석·다리. */
@Composable
fun SeatLine(x: Int, y: Int, color: Color, stroke: Float = 7f, raised: Boolean = false) = Canvas(Modifier.at(x, y, 203, 192)) {
    val d = density
    fun o(px: Float, py: Float) = Offset(px * d, py * d)
    drawLine(color, o(9f, 192f), o(203f, 192f), stroke)                              // 바닥
    drawLine(color, o(95f, 152f), o(95f, 192f), stroke); drawLine(color, o(154f, 152f), o(154f, 192f), stroke)   // 다리
    val cushion = Path(); cushion.moveTo(62f * d, 120f * d); cushion.lineTo(185f * d, if (raised) 140f * d else 153f * d); drawPath(cushion, color, style = Stroke(stroke, cap = StrokeCap.Round))
    val back = Path(); back.moveTo(15f * d, 32f * d); back.lineTo(if (raised) 60f * d else 91f * d, 144f * d); drawPath(back, color, style = Stroke(stroke, cap = StrokeCap.Round))
    drawCircle(color, 15f * d, o(19f, 18f), style = Stroke(stroke))                   // 머리받침
}

/** 홈 좌석 도식. (218,424) 460×640 차 윤곽 + 운전석·조수석·2열 세 칸. */
data class SeatTile(val label: String, val sub: String, val sub2: String? = null, val highlight: Boolean = false, val onClick: (() -> Unit)? = null, val onLongClick: (() -> Unit)? = null)
@Composable
fun SeatMap(tiles: Map<Seat, SeatTile>, driverSeated: Boolean?, footnote: String) = Box(Modifier.at(218, 424, 460, 640).background(C.white, RoundedCornerShape(120.dp)).border(3.dp, C.line, RoundedCornerShape(120.dp))) {
    Canvas(Modifier.at(65, 100, 330, 25)) { drawArc(C.line, 200f, 140f, false, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, size.height * 2.4f), style = Stroke(4f)) }   // 앞유리
    T(220, 30, "앞", 22, 700, C.text3)
    Tile(45, 165, 150, 160, SeatTile("운전석", if (driverSeated == true) "보호자" else "비어 있음"), dim = true)
    Tile(265, 165, 150, 160, tiles.getValue(Seat.PASSENGER))
    Tile(35, 400, 120, 140, tiles.getValue(Seat.REAR_LEFT))
    R(170, 400, 120, 140, C.seatMid, 28, C.seatMidLine to 3) { Text("가운데", Modifier.align(Alignment.Center).offset(y = (-16).dp), color = C.text3, style = ts(24, 700)) }
    Tile(305, 400, 120, 140, tiles.getValue(Seat.REAR_RIGHT))
    T(0, 654, footnote, 24, 400, C.text2, w = 460, align = TextAlign.Center)
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Tile(x: Int, y: Int, w: Int, h: Int, t: SeatTile, dim: Boolean = false) {
    var m = Modifier.at(x, y, w, h).background(if (dim) C.seatOff else if (t.highlight) C.mint else C.card, RoundedCornerShape(28.dp))
    m = if (t.highlight) m.border(5.dp, C.toggle, RoundedCornerShape(28.dp)) else if (!dim) m.border(3.dp, C.line, RoundedCornerShape(28.dp)) else m
    if (t.onClick != null || t.onLongClick != null) m = m.combinedClickable(onClick = { t.onClick?.invoke() }, onLongClick = t.onLongClick)
    Box(m) {
        Text(t.label, Modifier.align(Alignment.TopCenter).offset(y = (h * 0.4).dp), color = if (dim) C.text3 else if (t.highlight) C.greenText else C.ink, style = ts(24, 700))
        Text(t.sub, Modifier.align(Alignment.BottomCenter).offset(y = (if (t.sub2 != null) -24 else -8).dp), color = C.text2, style = ts(20, 400))
        if (t.sub2 != null) Text(t.sub2, Modifier.align(Alignment.BottomCenter).offset(y = (-2).dp), color = C.greenText, style = ts(20, 700))
    }
}

/** 신호 진행 점 세 개(곧 움직여요 · 기다려 주세요 · 됐어요). y는 Figma 점 행(07: 848). fg = 글 색. */
@Composable
fun Dots(y: Int, active: Int, fg: Color) {
    val xs = listOf(490, 850, 1210); val labels = listOf("곧 움직여요", "기다려 주세요", "됐어요"); val lw = listOf(118, 141, 67)
    Canvas(Modifier.at(520, y + 16, 660, 1)) { drawLine(fg, Offset(0f, 0f), Offset(300.dp.toPx(), 0f), 3f); drawLine(fg, Offset(360.dp.toPx(), 0f), Offset(660.dp.toPx(), 0f), 3f) }
    for (i in 0..2) {
        val on = i == active; val r = if (on) 16 else 11
        Box(Modifier.at(xs[i] - r, y + 16 - r, 2 * r, 2 * r).background(if (on) fg else fg.copy(alpha = 0.45f), CircleShape))
        T(xs[i] - lw[i] / 2, y + 42, labels[i], 26, if (on) 700 else 400, if (on) fg else fg.copy(alpha = 0.7f), w = lw[i], align = TextAlign.Center)
    }
}

/** 단계 칩 줄(05: 「✓ 뒤로 · 등받이 … · 높이」). 가운데 정렬, 칩 폭 200(이름 5자 이상이면 240), 간격 30. */
@Composable
fun StepChips(y: Int, steps: List<String>, index: Int) {
    if (steps.isEmpty()) return
    val w = if (steps.any { it.length >= 5 }) 240 else 200; val total = steps.size * w + (steps.size - 1) * 30
    var x = 850 - total / 2
    steps.forEachIndexed { i, s ->
        val cur = i == index; val done = i < index
        R(x, y, w, 64, C.white.copy(alpha = if (cur) 0.32f else 0.18f), 32) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                if (done) { CheckIcon(20, 15, C.white, 4f); Spacer(Modifier.width(17.dp)) }
                Text(if (cur) "$s …" else s, color = C.white, style = ts(26, 700))
            }
        }
        x += w + 30
    }
}

/** 띠 뒤의 회색 격자(내비 자리표시). 설계서 7절 3항. */
@Composable
fun MapGrid() {
    Canvas(Modifier.fillMaxSize()) {
        for (yy in listOf(120, 250, 380, 510, 640, 770, 900, 1030)) drawLine(C.mapLine, Offset(0f, yy.dp.toPx()), Offset(size.width, yy.dp.toPx()), 26.dp.toPx())
        for (xx in listOf(170, 420, 670, 920, 1170, 1420)) drawLine(C.mapLine, Offset(xx.dp.toPx(), 40.dp.toPx()), Offset(xx.dp.toPx(), size.height), 22.dp.toPx())
        val p = Path(); p.moveTo(1400.dp.toPx(), 170.dp.toPx()); p.lineTo(1400.dp.toPx(), 620.dp.toPx()); p.lineTo(850.dp.toPx(), 620.dp.toPx()); p.lineTo(850.dp.toPx(), 1000.dp.toPx())
        drawPath(p, C.blue, style = Stroke(26.dp.toPx(), cap = StrokeCap.Round))
    }
    R(814, 954, 72, 110, C.dark, 24)
    T(1441, 1098, "내비게이션 (다른 앱)", 26, 400, C.text3, w = 211, align = TextAlign.End)
}

/** 팝업 뒤 반투명 검정. */
@Composable
fun Scrim(onClick: (() -> Unit)? = null) = R(0, 0, 1700, 1184, Color.Black.copy(alpha = 0.5f), 32, onClick = onClick ?: {})

/** 주의 삼각형(온보딩 「시동을 켜 두세요」). (898,349) 64×56. */
@Composable
fun WarnIcon(x: Int, y: Int) = Canvas(Modifier.at(x, y, 64, 56)) {
    val p = Path(); p.moveTo(size.width / 2, 0f); p.lineTo(size.width, size.height); p.lineTo(0f, size.height); p.close()
    drawPath(p, C.amber, style = Stroke(5f, join = androidx.compose.ui.graphics.StrokeJoin.Round)); drawPath(p, C.amber)
    drawLine(C.white, Offset(size.width / 2, size.height * 0.38f), Offset(size.width / 2, size.height * 0.7f), 6.4f, StrokeCap.Round)
    drawCircle(C.white, 4f, Offset(size.width / 2, size.height * 0.84f))
}

/** 05~09·19~22 부모님용 전체화면. 바탕·글 색은 kind로. */
@Composable
fun FullSignal(sig: dev.gwanho.pleossample.Signal, on: (dev.gwanho.pleossample.UiEvent) -> Unit, lit: Boolean = false) {
    val (bg, fg) = when (sig.kind) { SignalKind.SOON -> C.amber to C.ink; SignalKind.WAIT -> C.red to C.white; SignalKind.DONE -> C.green to C.white }
    val title = when (sig.kind) { SignalKind.SOON -> "곧 움직여요"; SignalKind.WAIT -> "기다려 주세요"; SignalKind.DONE -> "됐어요" }
    val sub = when (sig.ctx to sig.kind) {
        SignalCtx.BOARD to SignalKind.WAIT -> "시트가 움직이고 있어요"
        SignalCtx.BOARD to SignalKind.DONE -> "이제 타셔도 돼요"
        SignalCtx.RIDE to SignalKind.SOON -> "등받이를 세울게요 · 기대지 마세요"
        SignalCtx.RIDE to SignalKind.WAIT -> "등받이를 세우고 있어요"
        SignalCtx.RIDE to SignalKind.DONE -> "다 됐어요 · 편하게 계세요"
        SignalCtx.EXIT to SignalKind.SOON -> "내리기 편하게 자리를 바꿀게요 · 기대지 마세요"
        SignalCtx.EXIT to SignalKind.WAIT -> "등받이를 세우고 시트를 높이고 있어요"
        else -> "문을 열어 드릴 때까지 앉아 계세요"
    }
    val sound = when (sig.kind) { SignalKind.WAIT -> "띵 · 띵"; SignalKind.DONE -> "딩동"; SignalKind.SOON -> "「등받이를 세울게요」" }
    Canvas1700(bg) {
        R(48, 44, 200, 72, fg.copy(alpha = 0.14f), 36) { Text(sig.seat.label, Modifier.align(Alignment.Center), color = fg, style = ts(30, 700)) }
        Box(Modifier.at(48, 44, 1604, 72)) {
            Row(Modifier.align(Alignment.CenterEnd).height(72.dp).background(fg.copy(alpha = 0.14f), RoundedCornerShape(36.dp)).padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                SoundIcon(0, 0, fg); Spacer(Modifier.width(16.dp)); Text(sound, color = fg, style = ts(28, 600))
            }
        }
        R(746, 146, 208, 208, fg.copy(alpha = 0.14f), 104) {
            when (sig.kind) {
                SignalKind.SOON -> { Box(Modifier.at(12, 12, 184, 184).border(10.dp, fg, CircleShape)); Text("${sig.countdown ?: 3}", Modifier.align(Alignment.Center), color = fg, style = ts(112, 700)) }
                SignalKind.WAIT -> PauseIcon(68, 58, fg)
                SignalKind.DONE -> CheckIcon(91, 70, fg, 16f, Modifier.at(61, 68))
            }
        }
        T(0, 381, title, 120, 800, fg, w = 1700, align = TextAlign.Center)
        T(0, 537, sub, 48, 700, fg, w = 1700, align = TextAlign.Center)
        val showArt = sig.kind == SignalKind.WAIT && sig.ctx != SignalCtx.RIDE
        if (showArt) SeatLine(744, 652, fg, raised = sig.ctx == SignalCtx.EXIT)
        if (sig.kind == SignalKind.WAIT && sig.ctx != SignalCtx.RIDE) StepChips(900, sig.steps, sig.stepIndex)
        val guardian = when (sig.ctx to sig.kind) {   // 보호자용 한 줄(가는 동안: 출발, 내릴 때: 문)
            SignalCtx.RIDE to SignalKind.SOON -> "보호자님, 출발은 '딩동' 뒤에 해 주세요" to "부모님 자리를 맞추는 데 몇 초 걸려요. 다 되면 소리와 화면으로 알려 드려요."
            SignalCtx.RIDE to SignalKind.WAIT -> "출발은 잠시 뒤에" to "다 되면 '딩동'과 함께 「이제 출발하셔도 돼요」라고 알려 드려요."
            SignalCtx.RIDE to SignalKind.DONE -> "이제 출발하셔도 돼요" to "이 화면은 잠시 뒤, 또는 기어를 D로 바꾸면 닫혀요."
            SignalCtx.EXIT to SignalKind.SOON -> "보호자님, 문은 '딩동' 뒤에 열어 주세요" to "부모님 자리를 맞추는 데 몇 초 걸려요. 다 되면 소리와 화면으로 알려 드려요."
            SignalCtx.EXIT to SignalKind.DONE -> "문을 열어 드리세요" to "부모님께는 문을 열어 드릴 때까지 앉아 계시라고 알려 드렸어요."
            else -> null
        }
        guardian?.let { (gt, gd) -> R(230, 640, 1240, 170, C.white.copy(alpha = 0.94f), 32); WheelIcon(276, 681, C.ink); T(400, 668, gt, 40, 700); T(400, 732, gd, 26, 400, C.text2, w = 1030) }
        if (sig.ctx == SignalCtx.RIDE) Dots(848, sig.kind.ordinal, fg)
        if (sig.ctx == SignalCtx.EXIT && sig.kind == SignalKind.SOON) Dots(848, 0, fg)
        if (sig.ctx == SignalCtx.BOARD && sig.kind == SignalKind.DONE) {
            if (lit) { R(450, 680, 380, 84, C.white.copy(alpha = 0.16f), 42); LightIcon(477, 697, C.white); T(540, 704, "${sig.seat.label} 독서등 켜짐", 28, 700, C.white) }   // 어두울 때만 켜짐
            val ax = if (lit) 870 else 660
            R(ax, 680, 380, 84, C.white.copy(alpha = 0.16f), 42); Box(Modifier.at(ax + 28, 698, 48, 48).border(3.dp, C.white, CircleShape)); Box(Modifier.at(ax + 34, 704, 36, 36).background(C.toggle, CircleShape)); T(ax + 90, 704, "실내 조명 초록", 28, 700, C.white)
            T(0, 1106, "부모님이 앉고 문이 닫힐 때까지 시트는 움직이지 않아요", 26, 400, C.white.copy(alpha = 0.85f), w = 1700, align = TextAlign.Center)
        }
        if (sig.ctx == SignalCtx.EXIT && sig.kind == SignalKind.DONE)
            T(0, 1106, "벨트를 푸시면 「다리를 하나씩 내놓고, 두 발이 땅에 닿은 뒤 일어나세요」라고 말해요", 26, 400, C.white.copy(alpha = 0.85f), w = 1700, align = TextAlign.Center)
        if (sig.kind != SignalKind.DONE) {
            R(620, 1026, 460, 110, C.white, 32, onClick = { on(dev.gwanho.pleossample.UiEvent.StopAll) }) { StopIcon(147, 42, 26, C.red); T(196, 29, "멈춤", 40, 800, C.red) }
            T(1108, 1065, "보호자용", 26, 400, fg.copy(alpha = 0.8f))
        }
    }
}

/** 주행 중 띠 (24,24) 1652×104. progress가 있으면 오른쪽 막대, 아니면 right 글만. icon = 시트 선화 또는 소리. */
@Composable
fun Band(text: String, right: String, progress: Float?, sound: Boolean) {
    R(24, 24, 1652, 104, C.ink.copy(alpha = 0.92f), 32)
    if (sound) SoundIcon(74, 55, C.white, 1.5f) else Canvas(Modifier.at(65, 38, 70, 72)) {
        val d = density
        drawLine(C.white, Offset(0f, 72 * d), Offset(70 * d, 72 * d), 4f); drawLine(C.white, Offset(31 * d, 58 * d), Offset(31 * d, 72 * d), 4f); drawLine(C.white, Offset(53 * d, 58 * d), Offset(53 * d, 72 * d), 4f)
        drawLine(C.white, Offset(19 * d, 46 * d), Offset(64 * d, 58 * d), 4f, StrokeCap.Round); drawLine(C.white, Offset(8 * d, 12 * d), Offset(30 * d, 54 * d), 4f, StrokeCap.Round)
        drawCircle(C.white, 5 * d, Offset(11 * d, 6 * d), style = Stroke(4f))
    }
    T(170, 54, text, 34, 700, C.white)
    if (progress != null) { R(1206, 64, 300, 16, C.white.copy(alpha = 0.25f), 8); R(1206, 64, (300 * progress.coerceIn(0.05f, 1f)).toInt(), 16, C.toggle, 8) }
    T(1206, 58, right, 28, 400, C.white, w = 430, align = TextAlign.End)
}

/** 시스템 팝업 틀(446, y) 808×h r48 + 반투명 배경. buttons = (글, 진한가, 동작). 버튼 폭은 수로 나눈다. */
@Composable
fun Dialog(y: Int, h: Int, title: String, body: String, buttons: List<Triple<String, Boolean, () -> Unit>>, content: @Composable () -> Unit = {}) {
    Scrim()
    R(446, y, 808, h, C.white, 48)
    T(494, y + 48, title, 40, 700); T(494, y + 116, body, 30, 400, C.text2, w = 712)
    content()
    val w = (712 - 24 * (buttons.size - 1)) / buttons.size
    buttons.forEachIndexed { i, (t, primary, act) -> Btn(494 + i * (w + 24), y + h - 135, w, 100, t, primary, onClick = act) }
}
