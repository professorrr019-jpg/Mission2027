package com.mission.launcher

/** Tiny safe calculator for the drawer search: + - * / % ^ and brackets, e.g. "12*(3+4)" or "2^10". */
object Calc {
    fun eval(input: String): String? {
        val s = input.replace("x", "*").replace("X", "*").replace("\u00d7", "*").replace("\u00f7", "/").replace(" ", "")
        if (s.length > 60 || !s.any { it in "+-*/%^" } || !s.all { it.isDigit() || it in "+-*/%^.()" }) return null
        return try {
            val p = Parser(s)
            val v = p.expr()
            if (p.i != s.length || v.isNaN() || v.isInfinite()) return null
            if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else String.format("%.6f", v).trimEnd('0').trimEnd('.')
        } catch (e: Exception) {
            null
        }
    }

    private class Parser(val s: String) {
        var i = 0

        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun term(): Double {
            var v = pow()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]
                val r = pow()
                v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
            }
            return v
        }

        fun pow(): Double {
            val b = unary()
            return if (i < s.length && s[i] == '^') { i++; Math.pow(b, pow()) } else b
        }

        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                if (i >= s.length || s[i] != ')') throw IllegalStateException()
                i++
                return v
            }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalStateException()
            return s.substring(st, i).toDouble()
        }
    }
}
