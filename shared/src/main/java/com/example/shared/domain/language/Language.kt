package com.example.shared.domain.language

import java.util.Locale

/**
 * One entry from [LanguageRegistry]. [code] is the stable identifier (what
 * gets persisted, passed as a nav argument, and fed to MADLAD's `<2xx>` tag
 * verbatim) -- everything else here is *derived*, not stored, so a script or
 * display-name quirk in one obscure language doesn't mean hand-editing a
 * 400-entry table.
 */
data class Language(val code: String, val englishName: String) {

    /**
     * ICU's likely-subtags data fills in the script Java's own [Locale]
     * doesn't expose directly -- same approach already proven in rmant7/AI's
     * TranslationActivity.isoScriptCode (used there for OmniTranslate's own
     * ISO-639-3+script prompt format). Null when ICU has no script for this
     * language, which does happen for some of MADLAD's more obscure codes.
     */
    val script: String? by lazy {
        runCatching {
            val likely = android.icu.util.ULocale.addLikelySubtags(
                android.icu.util.ULocale(code.replace('-', '_'))
            )
            likely.script?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /**
     * Derived from script, not hand-tagged per language like the old
     * SolutionLanguageOption.isRtl -- a script this table has never seen
     * before (one of MADLAD's ~370 codes beyond the original 49) still gets
     * the right text direction without anyone having to remember to flag it.
     */
    val isRtl: Boolean get() = script in RTL_SCRIPTS

    /** The language's own name for itself, e.g. "Deutsch" for German. Falls
     * back to [englishName] when ICU has nothing (common for MADLAD's more
     * obscure entries -- CLDR simply doesn't cover every one of them). */
    fun nativeName(): String = displayName(Locale.forLanguageTag(code))

    /** [englishName] localized into [uiLocale], e.g. "German" in an English
     * UI, "Немецкий" in a Russian one. Same ICU-gap fallback as [nativeName]. */
    fun displayName(uiLocale: Locale = Locale.getDefault()): String {
        val locale = Locale.forLanguageTag(code)
        val name = runCatching { locale.getDisplayName(uiLocale) }.getOrNull()
        // Locale.getDisplayName falls back to returning the code itself
        // (unlocalized) when it has nothing better -- that's not a real
        // localized name, so treat it the same as a blank/failed lookup.
        return name?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
            ?: englishName
    }

    /**
     * What actually goes in a model prompt -- replaces the old
     * SolutionLanguageOption.languageName ("Hebrew language"), a bare name
     * a small chat-tuned local model turned out not to reliably resolve: a
     * real device test asked TranslateGemma for target language "he" and
     * got Arabic back. Structured as "English name (code, Script)" --
     * "Seychellois Creole (crs, Latn)" -- so a name that's ambiguous or
     * unfamiliar on its own (common past the original 49 languages) still
     * carries an unambiguous code and script alongside it. Falls back to
     * "English name (code)" when ICU has no script for this language.
     */
    val promptName: String
        get() = script?.let { "$englishName ($code, $it)" } ?: "$englishName ($code)"

    private companion object {
        val RTL_SCRIPTS = setOf("Arab", "Hebr", "Thaa", "Syrc", "Nkoo", "Adlm", "Rohg", "Yezi", "Mand", "Samr")
    }
}
