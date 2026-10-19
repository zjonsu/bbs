package com.bbs.reader.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.data.model.FontOption
import com.bbs.reader.data.model.FontWeightOption
import com.bbs.reader.data.model.ReaderBackground
import com.bbs.reader.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class SettingsStore(private val context: Context) {
    private val key = stringPreferencesKey("settings_json")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = decode(prefs[key])
            prefs[key] = encode(transform(current))
        }
    }

    suspend fun set(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[key] = encode(settings)
        }
    }

    private fun encode(s: AppSettings): String =
        JSONObject()
            .put("themeMode", s.themeMode.name)
            .put("fontFamily", s.fontFamily.name)
            .put("fontWeight", s.fontWeight.name)
            .put("baseFontSizeSp", s.baseFontSizeSp.toDouble())
            .put("lineSpacingVertical", s.lineSpacingVertical.toDouble())
            .put("lineSpacingHorizontal", s.lineSpacingHorizontal.toDouble())
            .put("fontScaleVertical", s.fontScaleVertical.toDouble())
            .put("fontScaleHorizontal", s.fontScaleHorizontal.toDouble())
            .put("marginHorizontalDp", s.marginHorizontalDp.toDouble())
            .put("marginVerticalDp", s.marginVerticalDp.toDouble())
            .put("readerBackground", s.readerBackground.name)
            .put("fontColorHex", s.fontColorHex)
            .toString()

    private fun decode(json: String?): AppSettings {
        if (json.isNullOrBlank()) return AppSettings()
        return try {
            val o = JSONObject(json)
            val family = FontOption.fromStored(o.optString("fontFamily", FontOption.ORIGINAL.name))
            val weight = FontWeightOption.fromStored(
                o.optString("fontWeight", FontWeightOption.REGULAR.name)
            )
            AppSettings(
                themeMode = ThemeMode.fromStored(o.optString("themeMode", ThemeMode.SYSTEM.name)),
                fontFamily = family,
                fontWeight = family.coerceWeight(weight),
                baseFontSizeSp = o.optDouble("baseFontSizeSp", 18.0).toFloat(),
                lineSpacingVertical = o.optDouble("lineSpacingVertical", 1.6).toFloat(),
                lineSpacingHorizontal = o.optDouble("lineSpacingHorizontal", 0.04).toFloat(),
                fontScaleVertical = o.optDouble("fontScaleVertical", 1.0).toFloat(),
                fontScaleHorizontal = o.optDouble("fontScaleHorizontal", 1.0).toFloat(),
                marginHorizontalDp = o.optDouble("marginHorizontalDp", 12.0).toFloat(),
                marginVerticalDp = o.optDouble("marginVerticalDp", 12.0).toFloat(),
                readerBackground = ReaderBackground.fromStored(
                    o.optString("readerBackground", ReaderBackground.WHITE.name)
                ),
                fontColorHex = o.optString("fontColorHex", "#1C1C1E")
            )
        } catch (_: Exception) {
            AppSettings()
        }
    }
}
