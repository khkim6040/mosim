package dev.gwanho.pleossample.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma fills. 이 값만 쓴다. */
object C {
    val ink = Color(0xFF131417); val text2 = Color(0xFF5D6068); val text3 = Color(0xFF8B8E95); val text4 = Color(0xFF9AA0A8)
    val line = Color(0xFFE0E1E6); val line2 = Color(0xFFCFD3D9); val bg = Color(0xFFF1F2F4); val card = Color(0xFFF7F8FA); val chip = Color(0xFFE3E6EA)
    val dark = Color(0xFF44464E); val white = Color.White; val blue = Color(0xFF0064FF); val red = Color(0xFFB42318)
    val green = Color(0xFF0B7D43); val greenText = Color(0xFF067A3F); val toggle = Color(0xFF02C265); val amber = Color(0xFFFF8A00)
    val amberText = Color(0xFFB35C00); val cream = Color(0xFFFFF4E5); val mint = Color(0xFFE7F8EF); val info = Color(0xFFEAF2FF)
    val map = Color(0xFFE9EDF2); val mapLine = Color(0xFFDDE2E8); val seatOff = Color(0xFFECEEF1); val seatMid = Color(0xFFF4F5F6); val seatMidLine = Color(0xFFC9CCD2)
    val knob = Color(0xFFB9BCC2); val danger = Color(0xFFF62E24); val bar = Color(0xFFC9CCD2)
}

/** 실차 밀도가 달라 1700dp 캔버스가 창에 안 맞을 때 손으로 보정하는 배율. 에뮬레이터는 1.0. */
const val SCALE = 1.0f

/** Figma 앱 창 1700×1184 캔버스를 실제 창 크기에 맞춰 축소해 가운데 둔다. 안에서는 Figma 좌표를 그대로 쓴다. */
@Composable
fun Canvas1700(bg: Color = C.white, content: @Composable BoxScope.() -> Unit) = BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
    val s = minOf(maxWidth / 1700.dp, maxHeight / 1184.dp) * SCALE
    Box(Modifier.align(Alignment.Center).requiredSize(1700.dp, 1184.dp).graphicsLayer { scaleX = s; scaleY = s }.background(bg, RoundedCornerShape(32.dp)), content = content)
}

fun Modifier.at(x: Int, y: Int) = offset(x.dp, y.dp)
fun Modifier.at(x: Int, y: Int, w: Int, h: Int) = offset(x.dp, y.dp).size(w.dp, h.dp)

/** Figma 글꼴 크기·굵기 → TextStyle. 줄 높이 = 1.3 × 크기(Figma 글 상자 높이). */
internal fun ts(size: Int, w: Int) = TextStyle(fontSize = size.sp, lineHeight = (size * 1.3f).sp,
    fontWeight = when { w >= 800 -> FontWeight.ExtraBold; w >= 700 -> FontWeight.Bold; w >= 600 -> FontWeight.SemiBold; else -> FontWeight.Normal },
    platformStyle = PlatformTextStyle(includeFontPadding = false))

/** Figma 텍스트 한 덩이. (x, y)는 글 상자 왼쪽 위, w가 있으면 그 폭 안에서 정렬·줄바꿈. */
@Composable
fun T(x: Int, y: Int, text: String, size: Int, weight: Int = 400, color: Color = C.ink, w: Int? = null, align: TextAlign = TextAlign.Start, modifier: Modifier = Modifier) =
    Text(text, modifier.at(x, y).let { if (w != null) it.width(w.dp) else it }, color = color, textAlign = align, style = ts(size, weight))

/** 둥근 사각형(배경·테두리·클릭). border = 색 to 두께. */
@Composable
fun R(x: Int, y: Int, w: Int, h: Int, color: Color, r: Int = 0, border: Pair<Color, Int>? = null, onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit = {}) {
    var m = Modifier.at(x, y, w, h).background(color, RoundedCornerShape(r.dp))
    if (border != null) m = m.border(border.second.dp, border.first, RoundedCornerShape(r.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, content = content)
}
