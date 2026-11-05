package com.cincikgames.not

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** El yazısından gelen bir satırdaki matematik ifadesini bulur ve hesaplar. */
object MathEval {

    class MathError(message: String) : Exception(message)

    /** Satırdan hesaplanabilir ifadeyi çıkarır ("toplam 12 x 3 =" -> "12*3"). Yoksa null. */
    fun extractExpression(line: String): String? {
        var s = line.substringBefore('=')
        s = s.replace('−', '-').replace('–', '-').replace('—', '-')
            .replace('×', '*').replace('·', '*').replace('÷', '/')
        // Rakamlar arasındaki x / X / : işaretlerini çarpma / bölme say.
        s = s.replace(Regex("(?<=[0-9)])\\s*[xX]\\s*(?=[0-9(])"), "*")
        s = s.replace(Regex("(?<=[0-9)])\\s*:\\s*(?=[0-9(])"), "/")
        s = s.replace(',', '.').replace(" ", "")
        val candidates = Regex("[0-9.()+\\-*/^√]+").findAll(s)
            .map { it.value }
            .filter { it.any(Char::isDigit) }
            .toList()
        return candidates.lastOrNull()
    }

    fun evaluate(expression: String): Double {
        val p = Parser(expression)
        val v = p.parseExpression()
        if (!p.atEnd()) throw MathError("Anlaşılmayan işaret: '${expression.substring(p.pos)}'")
        if (v.isNaN() || v.isInfinite()) throw MathError("Sonuç tanımsız")
        return v
    }

    fun format(value: Double, useComma: Boolean): String {
        val text = if (value == Math.rint(value) && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            BigDecimal(value).round(MathContext(12)).stripTrailingZeros().toPlainString()
        }
        return if (useComma) text.replace('.', ',') else text
    }

    /** Satırı "ifade = sonuç" haline getirir. Hesaplanamazsa MathError fırlatır. */
    fun solveLine(line: String): String {
        val expr = extractExpression(line) ?: throw MathError("Bu satırda hesaplanacak işlem bulunamadı")
        val result = evaluate(expr)
        val left = line.substringBefore('=').trimEnd()
        return "$left = ${format(result, line.contains(','))}"
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        private fun peek(): Char? = if (pos < s.length) s[pos] else null

        fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; v += parseTerm() }
                    '-' -> { pos++; v -= parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parseUnary()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; v *= parseUnary() }
                    '/' -> {
                        pos++
                        val d = parseUnary()
                        if (d == 0.0) throw MathError("Sıfıra bölünemez")
                        v /= d
                    }
                    '(' -> v *= parseUnary() // 2(3+4) gibi gizli çarpma
                    else -> return v
                }
            }
        }

        private fun parseUnary(): Double = when (peek()) {
            '-' -> { pos++; -parseUnary() }
            '+' -> { pos++; parseUnary() }
            '√' -> {
                pos++
                val v = parseUnary()
                if (v < 0) throw MathError("Negatif sayının karekökü alınamaz")
                sqrt(v)
            }
            else -> parsePower()
        }

        private fun parsePower(): Double {
            val base = parsePrimary()
            if (peek() == '^') {
                pos++
                return base.pow(parseUnary())
            }
            return base
        }

        private fun parsePrimary(): Double {
            val c = peek() ?: throw MathError("İşlem eksik bitti")
            if (c == '(') {
                pos++
                val v = parseExpression()
                if (peek() != ')') throw MathError("Kapanmayan parantez")
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw MathError("Burada sayı bekleniyordu: '$c'")
            return s.substring(start, pos).toDoubleOrNull()
                ?: throw MathError("Geçersiz sayı: ${s.substring(start, pos)}")
        }
    }
}
