package com.gasprice.domain.usecase

import com.gasprice.domain.model.ParsedPrice
import com.gasprice.domain.model.ParsingStatus

/**
 * Parses spoken or typed gas price strings into normalized numeric values.
 *
 * Handles formats:
 *  - "149.9" -> 149.9
 *  - "one forty nine point nine" -> 149.9
 *  - "one dollar forty nine point nine" -> 149.9 (treated as cents/litre display)
 *  - "one sixty seven" -> 167.0
 *  - "1.679" -> 167.9  (pump price in dollars, normalize to cents/litre)
 *  - "167.9" -> 167.9  (already cents/litre)
 *
 * Canadian gas prices: displayed as cents per litre (e.g. 167.9 cents/L).
 * Spoken as "one sixty seven nine" or "one sixty seven point nine".
 * Pump display can show "1.679" (dollars per litre) -> multiply by 100.
 *
 * Returns ParsedPrice with status indicating confidence.
 */
object GasPriceParsing {

    fun parse(input: String): ParsedPrice {
        val raw = input.trim()
        if (raw.isBlank()) {
            return ParsedPrice(null, ParsingStatus.FAILED, raw)
        }

        // 1. Try direct numeric parse first
        val directNumeric = tryDirectNumeric(raw)
        if (directNumeric != null) {
            val normalized = normalizePrice(directNumeric)
            return if (normalized != null) {
                ParsedPrice(normalized, ParsingStatus.SUCCESS, raw)
            } else {
                ParsedPrice(null, ParsingStatus.FAILED, raw)
            }
        }

        // 2. Try word-to-number conversion
        val fromWords = tryWordToNumber(raw.lowercase())
        if (fromWords != null) {
            val normalized = normalizePrice(fromWords)
            return if (normalized != null) {
                ParsedPrice(normalized, ParsingStatus.SUCCESS, raw)
            } else {
                ParsedPrice(fromWords, ParsingStatus.LOW_CONFIDENCE, raw)
            }
        }

        return ParsedPrice(null, ParsingStatus.FAILED, raw)
    }

    /**
     * Normalize price to a sensible gas price range.
     * - If < 2.0: treat as dollars-per-litre pump display, multiply by 100 (e.g. 1.679 -> 167.9)
     * - If 2.0..10.0: ambiguous; likely dollars (US), keep as-is, LOW_CONFIDENCE
     * - If 10.0..350.0: cents/litre (CA) or US cents display — valid range, accept
     * - Otherwise: out of range, fail
     */
    private fun normalizePrice(value: Double): Double? {
        return when {
            value < 0 -> null
            value < 2.0 -> value * 100  // e.g. 1.679 -> 167.9 (Canadian pump display)
            value <= 10.0 -> value      // e.g. 3.49 USD/gallon — keep
            value <= 350.0 -> value     // e.g. 167.9 cents/L or 349.9 US cents/gallon
            else -> null                // > 350 is implausible
        }
    }

    private fun tryDirectNumeric(input: String): Double? {
        // Strip currency symbols
        val cleaned = input.replace(Regex("[$¢€£,]"), "").trim()
        return cleaned.toDoubleOrNull()
    }

    /**
     * Convert spoken English number phrases to a Double.
     * Handles: "one forty nine point nine", "one dollar forty nine", "one sixty seven"
     */
    private fun tryWordToNumber(input: String): Double? {
        // Remove filler words
        val cleaned = input
            .replace(Regex("\\b(dollar|dollars|cent|cents|per|litre|liter|gallon|and|a)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Canadian pump prices are often spoken without "point": "one sixty seven nine".
        tryCompactCanadianPumpPhrase(cleaned)?.let { return it }

        // If it contains "point" or "dot", split on it
        val pointIndex = cleaned.indexOf("point").takeIf { it >= 0 }
            ?: cleaned.indexOf("dot").takeIf { it >= 0 }

        return if (pointIndex != null) {
            val intPart = cleaned.substring(0, pointIndex).trim()
            val decPart = cleaned.substring(pointIndex + cleaned.substring(pointIndex)
                .takeWhile { it.isLetter() }.length).trim()
            val intVal = wordsToInt(intPart) ?: return null
            val decVal = wordsToInt(decPart) ?: return null
            "$intVal.$decVal".toDoubleOrNull()
        } else {
            val intVal = wordsToInt(cleaned) ?: return null
            intVal.toDouble()
        }
    }

    private fun tryCompactCanadianPumpPhrase(input: String): Double? {
        val words = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size != 4) return null

        val hundreds = mapOf("one" to 1, "two" to 2, "three" to 3)
        val tens = mapOf(
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
            "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
        )
        val ones = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
            "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9
        )

        val whole = (hundreds[words[0]] ?: return null) * 100 +
            (tens[words[1]] ?: return null) +
            (ones[words[2]] ?: return null)
        val decimal = ones[words[3]] ?: return null
        return whole.toDouble() + (decimal.toDouble() / 10.0)
    }

    /**
     * Convert a sequence of number words to an integer.
     * Supports: ones, teens, tens, hundreds.
     */
    private fun wordsToInt(input: String): Int? {
        val words = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        // Try direct digit string first
        if (words.size == 1) {
            words[0].toIntOrNull()?.let { return it }
        }

        val ones = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
            "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
            "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
            "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
            "eighteen" to 18, "nineteen" to 19
        )
        val tens = mapOf(
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
            "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
        )

        if (
            words.size >= 2 &&
            words.none { it == "hundred" || it == "thousand" } &&
            words.first() in ones &&
            words[1] in tens
        ) {
            val hundreds = ones[words.first()]!! * 100
            val remainder = words.drop(1).sumOf { word ->
                ones[word] ?: tens[word] ?: return null
            }
            return hundreds + remainder
        }

        var total = 0
        var current = 0

        for (word in words) {
            val w = word.lowercase()
            when {
                ones.containsKey(w) -> current += ones[w]!!
                tens.containsKey(w) -> current += tens[w]!!
                w == "hundred" -> current = if (current == 0) 100 else current * 100
                w == "thousand" -> {
                    total += if (current == 0) 1000 else current * 1000
                    current = 0
                }
                w.toIntOrNull() != null -> current += w.toInt()
                else -> return null  // Unknown word
            }
        }
        return total + current
    }
}
