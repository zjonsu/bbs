package com.bbs.reader.data.model

enum class ThemeMode(val label: String) {
    SYSTEM("시스템"),
    LIGHT("화이트"),
    DARK("다크");

    companion object {
        fun fromStored(name: String): ThemeMode =
            entries.find { it.name == name } ?: SYSTEM
    }
}

enum class FontOption(val label: String) {
    ORIGINAL("원본"),
    NANUM_MYUNGJO("나눔명조"),
    NANUM_BARUN_GOTHIC("나눔바른고딕"),
    NANUM_HUMAN("나눔휴먼");

    fun availableWeights(): List<FontWeightOption> = when (this) {
        ORIGINAL -> listOf(FontWeightOption.REGULAR, FontWeightOption.BOLD)
        NANUM_MYUNGJO -> listOf(
            FontWeightOption.REGULAR,
            FontWeightOption.BOLD,
            FontWeightOption.EXTRA_BOLD
        )
        NANUM_BARUN_GOTHIC -> listOf(
            FontWeightOption.ULTRA_LIGHT,
            FontWeightOption.LIGHT,
            FontWeightOption.REGULAR,
            FontWeightOption.BOLD
        )
        NANUM_HUMAN -> listOf(
            FontWeightOption.EXTRA_LIGHT,
            FontWeightOption.LIGHT,
            FontWeightOption.REGULAR,
            FontWeightOption.BOLD,
            FontWeightOption.EXTRA_BOLD,
            FontWeightOption.HEAVY
        )
    }

    fun coerceWeight(weight: FontWeightOption): FontWeightOption {
        val avail = availableWeights()
        if (weight in avail) return weight
        return avail.find { it == FontWeightOption.REGULAR } ?: avail.first()
    }

    /** WebView @font-face / assets 경로용 파일명 (확장자 포함). ORIGINAL은 null. */
    fun assetFileName(weight: FontWeightOption): String? {
        val w = coerceWeight(weight)
        return when (this) {
            ORIGINAL -> null
            NANUM_MYUNGJO -> when (w) {
                FontWeightOption.BOLD -> "nanum_myungjo_bold.ttf"
                FontWeightOption.EXTRA_BOLD -> "nanum_myungjo_extra_bold.ttf"
                else -> "nanum_myungjo.ttf"
            }
            NANUM_BARUN_GOTHIC -> when (w) {
                FontWeightOption.ULTRA_LIGHT -> "nanum_barun_gothic_ultra_light.otf"
                FontWeightOption.LIGHT -> "nanum_barun_gothic_light.otf"
                FontWeightOption.BOLD -> "nanum_barun_gothic_bold.otf"
                else -> "nanum_barun_gothic.otf"
            }
            NANUM_HUMAN -> when (w) {
                FontWeightOption.EXTRA_LIGHT -> "nanum_human_extra_light.ttf"
                FontWeightOption.LIGHT -> "nanum_human_light.ttf"
                FontWeightOption.BOLD -> "nanum_human_bold.ttf"
                FontWeightOption.EXTRA_BOLD -> "nanum_human_extra_bold.ttf"
                FontWeightOption.HEAVY -> "nanum_human_heavy.ttf"
                else -> "nanum_human.ttf"
            }
        }
    }

    fun cssFamilyName(): String = when (this) {
        ORIGINAL -> "system-ui"
        NANUM_MYUNGJO -> "NanumMyungjo"
        NANUM_BARUN_GOTHIC -> "NanumBarunGothic"
        NANUM_HUMAN -> "NanumHuman"
    }

    companion object {
        fun fromStored(name: String): FontOption =
            entries.find { it.name == name } ?: ORIGINAL
    }
}

enum class FontWeightOption(val label: String, val css: String, val composeWeight: Int) {
    ULTRA_LIGHT("아주 가는", "200", 200),
    EXTRA_LIGHT("아주 가는", "200", 200),
    LIGHT("가는", "300", 300),
    REGULAR("중간", "400", 400),
    BOLD("굵은", "700", 700),
    EXTRA_BOLD("아주 굵은", "800", 800),
    HEAVY("무거운", "900", 900);

    companion object {
        fun fromStored(name: String): FontWeightOption =
            entries.find { it.name == name } ?: REGULAR
    }
}

enum class ReaderBackground(val label: String, val hex: String, val night: Boolean = false) {
    WHITE("흰색", "#FFFFFF"),
    PAPER("크림", "#F7F3EB"),
    MINT("민트", "#E4F0E8"),
    SEPIA("세피아", "#F4ECD8"),
    SLATE("슬레이트", "#3A3A3C", night = true),
    CHARCOAL("차콜", "#2C2C2E", night = true),
    NIGHT("야간", "#121212", night = true);

    companion object {
        fun fromStored(name: String): ReaderBackground =
            entries.find { it.name == name } ?: WHITE
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontFamily: FontOption = FontOption.ORIGINAL,
    val fontWeight: FontWeightOption = FontWeightOption.REGULAR,
    val baseFontSizeSp: Float = 18f,
    val lineSpacingVertical: Float = 1.6f,
    val lineSpacingHorizontal: Float = 0.04f,
    val fontScaleVertical: Float = 1.0f,
    val fontScaleHorizontal: Float = 1.0f,
    val marginHorizontalDp: Float = 12f,
    val marginVerticalDp: Float = 12f,
    val readerBackground: ReaderBackground = ReaderBackground.WHITE,
    val fontColorHex: String = "#1C1C1E"
) {
    val effectiveFontSizeSp: Float
        get() = baseFontSizeSp * fontScaleVertical

    val effectiveLetterSpacingEm: Float
        get() = (lineSpacingHorizontal + (fontScaleHorizontal - 1f) * 0.06f).coerceAtLeast(0f)

    fun withFontFamily(family: FontOption): AppSettings =
        copy(fontFamily = family, fontWeight = family.coerceWeight(fontWeight))
}
