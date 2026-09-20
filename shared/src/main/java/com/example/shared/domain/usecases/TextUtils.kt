package com.example.shared.domain.usecases

import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.HtmlCompat
import com.example.shared.domain.prompt.options.SolutionLanguageOption
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.text.Bidi

object TextUtils {

    fun getTextDirection(text: String?): LayoutDirection {

        if (text == null) return LayoutDirection.Ltr

        val isLtr = Bidi(
            text,
            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
        ).isLeftToRight
        return if (isLtr) LayoutDirection.Ltr else LayoutDirection.Rtl
    }

    fun getTextDirection(languageOption: SolutionLanguageOption): LayoutDirection =
        if (languageOption.isRtl)
            LayoutDirection.Rtl
        else
            LayoutDirection.Ltr

    fun markdownToHtml(textWithMarkdown: String): String {
        val flavour = CommonMarkFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(textWithMarkdown)
        return HtmlGenerator(textWithMarkdown, parsedTree, flavour).generateHtml()
    }

    fun htmlToJsonString(text: String): String {
        val fromHtmlToPlainText =
            HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
        return fromHtmlToPlainText
            .toString()
            .replace("\"", "")
    }

}
