package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation

/**
 * Подчёркивает span ошибки в тексте. Длина текста не меняется, маппинг
 * тождественный.
 */
fun underlineError(result: Calculation, color: Color) =
    VisualTransformation { text ->
        val styled = AnnotatedString.Builder(text)
        if (result is Calculation.Failed) {
            val start = result.span.start.coerceIn(0, text.length)
            val end = result.span.end.coerceIn(start, text.length)
            styled.addStyle(
                SpanStyle(
                    color = color,
                    textDecoration = TextDecoration.Underline,
                ),
                start,
                end,
            )
        }
        TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
