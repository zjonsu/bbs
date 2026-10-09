package com.bbs.reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.data.model.FontOption
import com.bbs.reader.data.model.ReaderBackground
import com.bbs.reader.data.model.ThemeMode
import com.bbs.reader.ui.reader.toComposeFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val stored by viewModel.settings.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(stored) }
    val latestUpdate = rememberUpdatedState(viewModel::update)

    LaunchedEffect(stored) {
        // 외부(초기화 등)에서 저장된 값이 바뀌면 draft 동기화
        draft = stored
    }

    LaunchedEffect(draft) {
        delay(160)
        if (draft != stored) {
            latestUpdate.value { draft }
        }
    }

    fun apply(next: AppSettings) {
        draft = next
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("설정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.reset()
                        draft = AppSettings()
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "초기화")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            SectionLabel("앱 테마")
            ChipRow {
                ThemeMode.entries.forEach { mode ->
                    CompactChip(
                        label = mode.label,
                        selected = draft.themeMode == mode,
                        onClick = {
                            apply(
                                when (mode) {
                                    ThemeMode.DARK -> draft.copy(
                                        themeMode = mode,
                                        readerBackground = ReaderBackground.NIGHT,
                                        fontColorHex = "#F2F2F7"
                                    )
                                    ThemeMode.LIGHT -> draft.copy(
                                        themeMode = mode,
                                        readerBackground = ReaderBackground.WHITE,
                                        fontColorHex = "#1C1C1E"
                                    )
                                    ThemeMode.SYSTEM -> draft.copy(themeMode = mode)
                                }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("읽은 글 숨기기", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "리스트에서 읽은 글 제외",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = draft.hideReadPosts,
                    onCheckedChange = { apply(draft.copy(hideReadPosts = it)) }
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("본문 배경")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ReaderBackground.entries.forEach { bg ->
                    val selected = draft.readerBackground == bg
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(bg.hex)))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                },
                                shape = CircleShape
                            )
                            .clickable {
                                apply(
                                    draft.copy(
                                        readerBackground = bg,
                                        fontColorHex = if (bg.night) "#F2F2F7" else "#1C1C1E"
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bg.night) {
                            Icon(
                                Icons.Rounded.DarkMode,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("글자 색")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("#1C1C1E", "#3A3A3C", "#5C4033", "#F2F2F7", "#E8D5B7").forEach { hex ->
                    val selected = draft.fontColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                },
                                shape = CircleShape
                            )
                            .clickable { apply(draft.copy(fontColorHex = hex)) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("글꼴")
            ChipRow {
                FontOption.entries.forEach { opt ->
                    CompactChip(
                        label = opt.label,
                        selected = draft.fontFamily == opt,
                        onClick = { apply(draft.withFontFamily(opt)) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("굵기")
            ChipRow {
                draft.fontFamily.availableWeights().forEach { opt ->
                    CompactChip(
                        label = opt.label,
                        selected = draft.fontWeight == opt,
                        onClick = { apply(draft.copy(fontWeight = opt)) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("보기 설정")
            PillValueRow(
                icon = Icons.Rounded.FormatSize,
                title = "글자 크기",
                valueLabel = "${draft.baseFontSizeSp.roundToInt()}",
                progress = (draft.baseFontSizeSp - 12f) / 16f,
                onDragFraction = { f ->
                    draft = draft.copy(baseFontSizeSp = (12f + f * 16f).coerceIn(12f, 28f))
                },
                onMinus = {
                    draft = draft.copy(
                        baseFontSizeSp = (draft.baseFontSizeSp - 0.5f).coerceIn(12f, 28f)
                    )
                },
                onPlus = {
                    draft = draft.copy(
                        baseFontSizeSp = (draft.baseFontSizeSp + 0.5f).coerceIn(12f, 28f)
                    )
                }
            )
            Spacer(Modifier.height(6.dp))
            PillValueRow(
                icon = Icons.Rounded.FormatLineSpacing,
                title = "줄간격",
                valueLabel = String.format("%.1f", draft.lineSpacingVertical),
                progress = (draft.lineSpacingVertical - 1f) / 1.4f,
                onDragFraction = { f ->
                    draft = draft.copy(lineSpacingVertical = (1f + f * 1.4f).coerceIn(1f, 2.4f))
                },
                onMinus = {
                    draft = draft.copy(
                        lineSpacingVertical = (draft.lineSpacingVertical - 0.05f).coerceIn(1f, 2.4f)
                    )
                },
                onPlus = {
                    draft = draft.copy(
                        lineSpacingVertical = (draft.lineSpacingVertical + 0.05f).coerceIn(1f, 2.4f)
                    )
                }
            )
            Spacer(Modifier.height(6.dp))
            PillValueRow(
                icon = Icons.Rounded.SpaceBar,
                title = "자간",
                valueLabel = String.format("%.2f", draft.lineSpacingHorizontal),
                progress = draft.lineSpacingHorizontal / 0.2f,
                onDragFraction = { f ->
                    draft = draft.copy(lineSpacingHorizontal = (f * 0.2f).coerceIn(0f, 0.2f))
                },
                onMinus = {
                    draft = draft.copy(
                        lineSpacingHorizontal =
                            (draft.lineSpacingHorizontal - 0.01f).coerceIn(0f, 0.2f)
                    )
                },
                onPlus = {
                    draft = draft.copy(
                        lineSpacingHorizontal =
                            (draft.lineSpacingHorizontal + 0.01f).coerceIn(0f, 0.2f)
                    )
                }
            )
            Spacer(Modifier.height(6.dp))
            PillValueRow(
                icon = Icons.Rounded.TextFields,
                title = "세로 비율",
                valueLabel = String.format("%.2f", draft.fontScaleVertical),
                progress = (draft.fontScaleVertical - 0.7f) / 1.1f,
                onDragFraction = { f ->
                    draft = draft.copy(fontScaleVertical = (0.7f + f * 1.1f).coerceIn(0.7f, 1.8f))
                },
                onMinus = {
                    draft = draft.copy(
                        fontScaleVertical = (draft.fontScaleVertical - 0.02f).coerceIn(0.7f, 1.8f)
                    )
                },
                onPlus = {
                    draft = draft.copy(
                        fontScaleVertical = (draft.fontScaleVertical + 0.02f).coerceIn(0.7f, 1.8f)
                    )
                }
            )
            Spacer(Modifier.height(6.dp))
            PillValueRow(
                icon = Icons.Rounded.ViewAgenda,
                title = "여백 · 좌우",
                valueLabel = "${draft.marginHorizontalDp.roundToInt()}",
                progress = draft.marginHorizontalDp / 40f,
                onDragFraction = { f ->
                    draft = draft.copy(marginHorizontalDp = (f * 40f).coerceIn(0f, 40f))
                },
                onMinus = {
                    draft = draft.copy(
                        marginHorizontalDp = (draft.marginHorizontalDp - 1f).coerceIn(0f, 40f)
                    )
                },
                onPlus = {
                    draft = draft.copy(
                        marginHorizontalDp = (draft.marginHorizontalDp + 1f).coerceIn(0f, 40f)
                    )
                }
            )
            Spacer(Modifier.height(6.dp))
            PillValueRow(
                icon = Icons.Rounded.ViewAgenda,
                title = "여백 · 상하",
                valueLabel = "${draft.marginVerticalDp.roundToInt()}",
                progress = draft.marginVerticalDp / 48f,
                onDragFraction = { f ->
                    draft = draft.copy(marginVerticalDp = (f * 48f).coerceIn(0f, 48f))
                },
                onMinus = {
                    draft = draft.copy(
                        marginVerticalDp = (draft.marginVerticalDp - 1f).coerceIn(0f, 48f)
                    )
                },
                onPlus = {
                    draft = draft.copy(
                        marginVerticalDp = (draft.marginVerticalDp + 1f).coerceIn(0f, 48f)
                    )
                }
            )

            Spacer(Modifier.height(14.dp))
            PreviewCard(draft)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        content()
    }
}

@Composable
private fun CompactChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PillValueRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    progress: Float,
    onDragFraction: (Float) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    val fill = progress.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val active = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val content = MaterialTheme.colorScheme.onSurface
    var widthPx by remember { mutableStateOf(1) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(track)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(widthPx) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onDragFraction((change.position.x / widthPx).coerceIn(0f, 1f))
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill.coerceAtLeast(0.08f))
                .background(active)
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = content.copy(alpha = 0.75f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StepBtn(onClick = onMinus) {
                Icon(Icons.Rounded.Remove, "감소", tint = content, modifier = Modifier.size(15.dp))
            }
            Text(
                valueLabel,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp),
                maxLines = 1
            )
            StepBtn(onClick = onPlus) {
                Icon(Icons.Rounded.Add, "증가", tint = content, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun StepBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PreviewCard(settings: AppSettings) {
    val bg = Color(android.graphics.Color.parseColor(settings.readerBackground.hex))
    val fg = Color(android.graphics.Color.parseColor(settings.fontColorHex))
    val fontFamily = settings.fontFamily.toComposeFontFamily(settings.fontWeight)
    val sizeSp = settings.effectiveFontSizeSp
    val lineHeight = (sizeSp * settings.lineSpacingVertical).sp
    val letter = settings.effectiveLetterSpacingEm.em
    val hPad = settings.marginHorizontalDp.dp.coerceIn(4.dp, 40.dp)
    val vPad = settings.marginVerticalDp.dp.coerceIn(4.dp, 48.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .background(bg)
            .padding(horizontal = hPad, vertical = vPad)
    ) {
        Text(
            text = "미리보기 · ${settings.fontFamily.label}",
            color = fg.copy(alpha = 0.65f),
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = (sizeSp * 0.75f).sp,
                letterSpacing = letter
            )
        )
        Spacer(Modifier.height((4f * settings.lineSpacingVertical).dp))
        Text(
            text = "글자 크기는 게시판 리스트와 본문에 함께 적용됩니다.\n줄간격·여백은 본문 화면 기준입니다.",
            color = fg,
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = sizeSp.sp,
                lineHeight = lineHeight,
                letterSpacing = letter,
                fontWeight = FontWeight.Normal
            )
        )
    }
}
