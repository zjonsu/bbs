package com.bbs.reader.ui.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * 제스처 초기에 세로/가로 축을 잠가, 목록 스크롤과 좌우 페이저가 서로 가로채지 않게 한다.
 * - 세로 우세: X를 소비해 HorizontalPager가 넘어가지 않음
 * - 가로 우세: Y를 소비해 리스트가 같이 움직이지 않음
 */
class AxisLockNestedScrollConnection(
    private val ratio: Float = 1.5f,
    private val minSlop: Float = 8f
) : NestedScrollConnection {

    private enum class Axis { Vertical, Horizontal }

    private var locked: Axis? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (locked == null) {
            val ax = abs(available.x)
            val ay = abs(available.y)
            locked = when {
                ay > ax * ratio && ay > minSlop -> Axis.Vertical
                ax > ay * ratio && ax > minSlop -> Axis.Horizontal
                else -> null
            }
        }
        return when (locked) {
            Axis.Vertical -> Offset(available.x, 0f)
            Axis.Horizontal -> Offset(0f, available.y)
            null -> Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        locked = null
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        locked = null
        return Velocity.Zero
    }
}
