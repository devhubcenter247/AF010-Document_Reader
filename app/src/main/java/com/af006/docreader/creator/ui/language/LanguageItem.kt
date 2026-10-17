package com.af010.creator.docreader.ui.language

import androidx.annotation.DrawableRes
import com.af010.creator.docreader.R
import java.util.Locale

data class LanguageItem(
    @DrawableRes val flagId: Int,
    val name: String,         // tên bản ngữ: "English", "Tiếng Việt"
    val code: String,         // mã resource: "en", "pt_BR", "fa_rIR"
    val englishName: String,  // dòng phụ khi không phải ngôn ngữ máy
) {
    /** Tag BCP-47 cho AppCompatDelegate / Locale.forLanguageTag: "pt-BR", "fa-IR" */
    val languageTag: String
        get() = code.replace("_r", "-").replace('_', '-')

    /** Chữ hiện thay cờ khi cần: "EN", "PT-BR" */
    val badge: String
        get() = languageTag.uppercase()
}

/** Trạng thái hiển thị của một dòng trong danh sách */
data class LanguageUiModel(
    val item: LanguageItem,
    val isSelected: Boolean = false,
    val isDeviceLanguage: Boolean = false,
) {
    val title: String get() = item.name
    val subtitle: String get() = if (isDeviceLanguage) "Device language" else item.englishName
}

fun List<LanguageItem>.toUiModels(
    selectedCode: String?,
    deviceLocale: Locale = Locale.getDefault(),
): List<LanguageUiModel> {
    val deviceTag = deviceLocale.toLanguageTag()
    val deviceItem = firstOrNull { it.languageTag.equals(deviceTag, true) }
        ?: firstOrNull { it.languageTag.equals(deviceLocale.language, true) }
    // Device language not in the list: fall back to English so Continue always has a choice.
    val selected = selectedCode ?: deviceItem?.code ?: "en"
    return map {
        LanguageUiModel(
            item = it,
            isSelected = it.code == selected,
            isDeviceLanguage = it == deviceItem,
        )
    }.sortedByDescending { it.isDeviceLanguage } // ngôn ngữ máy lên đầu
}

val listLanguage = listOf(
    LanguageItem(R.drawable.ic_flag_uk, "English", "en", "English"),
    LanguageItem(R.drawable.ic_flag_india, "हिंदी", "hi", "Hindi"),
    LanguageItem(R.drawable.ic_flag_france, "Français", "fr", "French"),
    LanguageItem(R.drawable.ic_flag_spain, "Español", "es", "Spanish"),
    LanguageItem(R.drawable.ic_flag_portugal, "Português", "pt", "Portuguese"),
    LanguageItem(R.drawable.ic_flag_arab, "العربية", "ar", "Arabic"),
    LanguageItem(R.drawable.ic_flag_bulgary, "Български", "bg", "Bulgarian"),
    LanguageItem(R.drawable.ic_flag_czech, "Čeština", "cs", "Czech"),
    LanguageItem(R.drawable.ic_flag_denmark, "Dansk", "da", "Danish"),
    LanguageItem(R.drawable.ic_flag_germany, "Deutsch", "de", "German"),
    LanguageItem(R.drawable.ic_flag_greek, "Ελληνικά", "el", "Greek"),
    LanguageItem(R.drawable.ic_flag_iran, "فارسی", "fa_rIR", "Persian"),
    LanguageItem(R.drawable.ic_flag_finland, "Suomi", "fi", "Finnish"),
    LanguageItem(R.drawable.ic_flag_filipino, "Filipino", "fil", "Filipino"),
    LanguageItem(R.drawable.ic_flag_india, "ગુજરાતી", "gu", "Gujarati"),
    LanguageItem(R.drawable.ic_flag_hungary, "Magyar", "hu", "Hungarian"),
    LanguageItem(R.drawable.ic_flag_indonesia, "Bahasa Indonesia", "in", "Indonesian"),
    LanguageItem(R.drawable.ic_flag_italy, "Italiano", "it", "Italian"),
    LanguageItem(R.drawable.ic_flag_israel, "עברית", "iw", "Hebrew"),
    LanguageItem(R.drawable.ic_flag_japan, "日本語", "ja", "Japanese"),
    LanguageItem(R.drawable.ic_flag_india, "ಕನ್ನಡ", "kn", "Kannada"),
    LanguageItem(R.drawable.ic_flag_south_korea, "한국어", "ko", "Korean"),
    LanguageItem(R.drawable.ic_flag_india, "മലയാളം", "ml", "Malayalam"),
    LanguageItem(R.drawable.ic_flag_india, "मराठी", "mr", "Marathi"),
    LanguageItem(R.drawable.ic_flag_malaysia, "Melayu", "ms", "Malay"),
    LanguageItem(R.drawable.ic_flag_netherlands, "Nederlands", "nl", "Dutch"),
    LanguageItem(R.drawable.ic_flag_pakistan, "ਪੰਜਾਬੀ", "pa", "Punjabi"),
    LanguageItem(R.drawable.ic_flag_poland, "Polski", "pl", "Polish"),
    LanguageItem(R.drawable.ic_flag_romania, "Română", "ro", "Romanian"),
    LanguageItem(R.drawable.ic_flag_russia, "Русский", "ru", "Russian"),
    LanguageItem(R.drawable.ic_flag_serbia, "Српски", "sr", "Serbian"),
    LanguageItem(R.drawable.ic_flag_sweden, "Svenska", "sv", "Swedish"),
    LanguageItem(R.drawable.ic_flag_india, "தமிழ்", "ta", "Tamil"),
    LanguageItem(R.drawable.ic_flag_india, "తెలుగు", "te", "Telugu"),
    LanguageItem(R.drawable.ic_flag_thailand, "ไทย", "th", "Thai"),
    LanguageItem(R.drawable.ic_flag_turkey, "Türkçe", "tr", "Turkish"),
    LanguageItem(R.drawable.ic_flag_ukraine, "Українська", "uk", "Ukrainian"),
    LanguageItem(R.drawable.ic_flag_pakistan, "اردو", "ur", "Urdu"),
    LanguageItem(R.drawable.ic_flag_china, "简体中文", "zh", "Chinese (Simplified)"),
    LanguageItem(R.drawable.ic_flag_taiwan, "繁體中文", "zh_TW", "Chinese (Traditional)"),
    LanguageItem(R.drawable.ic_flag_vietnam, "Tiếng Việt", "vi", "Vietnamese"),
    LanguageItem(R.drawable.ic_flag_brazil, "Português (Brasil)", "pt_BR", "Portuguese (Brazil)"),
    LanguageItem(R.drawable.ic_flag_mexico, "Español (México)", "es_MX", "Spanish (Mexico)"),
    LanguageItem(R.drawable.ic_flag_pakistan, "اردو (پاکستان)", "ur_PK", "Urdu (Pakistan)"),
    LanguageItem(R.drawable.ic_flag_uae, "العربية (الإمارات)", "ar_AE", "Arabic (UAE)"),
    LanguageItem(R.drawable.ic_flag_arab, "العربية (السعودية)", "ar_SA", "Arabic (Saudi Arabia)"),
)