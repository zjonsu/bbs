package com.bbs.reader.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import com.bbs.reader.R
import com.bbs.reader.data.model.FontOption
import com.bbs.reader.data.model.FontWeightOption
import java.util.concurrent.ConcurrentHashMap

private val typefaceCache = ConcurrentHashMap<Int, Typeface>()

private fun FontOption.fontResForWeight(weight: FontWeightOption): Int? {
    val w = coerceWeight(weight)
    return when (this) {
        FontOption.ORIGINAL -> null
        FontOption.NANUM_MYUNGJO -> when (w) {
            FontWeightOption.BOLD -> R.font.nanum_myungjo_bold
            FontWeightOption.EXTRA_BOLD -> R.font.nanum_myungjo_extra_bold
            else -> R.font.nanum_myungjo
        }
        FontOption.NANUM_BARUN_GOTHIC -> when (w) {
            FontWeightOption.ULTRA_LIGHT -> R.font.nanum_barun_gothic_ultra_light
            FontWeightOption.LIGHT -> R.font.nanum_barun_gothic_light
            FontWeightOption.BOLD -> R.font.nanum_barun_gothic_bold
            else -> R.font.nanum_barun_gothic
        }
        FontOption.NANUM_HUMAN -> when (w) {
            FontWeightOption.EXTRA_LIGHT -> R.font.nanum_human_extra_light
            FontWeightOption.LIGHT -> R.font.nanum_human_light
            FontWeightOption.BOLD -> R.font.nanum_human_bold
            FontWeightOption.EXTRA_BOLD -> R.font.nanum_human_extra_bold
            FontWeightOption.HEAVY -> R.font.nanum_human_heavy
            else -> R.font.nanum_human
        }
    }
}

fun FontOption.toTypeface(
    context: Context,
    weight: FontWeightOption = FontWeightOption.REGULAR
): Typeface {
    val resolved = coerceWeight(weight)
    val resId = fontResForWeight(resolved)
    if (resId != null) {
        typefaceCache[resId]?.let { return it }
        val loaded = ResourcesCompat.getFont(context, resId) ?: Typeface.DEFAULT
        typefaceCache[resId] = loaded
        return loaded
    }
    val base = if (resolved.composeWeight >= 700) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, resolved.composeWeight, false)
    } else {
        Typeface.create(
            base,
            if (resolved.composeWeight >= 700) Typeface.BOLD else Typeface.NORMAL
        )
    }
}

@Composable
fun FontOption.toComposeFontFamily(weight: FontWeightOption): FontFamily {
    val context = LocalContext.current
    val typeface = remember(this, weight) { toTypeface(context, weight) }
    return remember(typeface) { FontFamily(typeface) }
}
