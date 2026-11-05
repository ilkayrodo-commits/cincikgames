package com.cincikgames.not

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MathEvalTest {
    @Test fun basic() {
        assertEquals("12+7 = 19", MathEval.solveLine("12+7="))
        assertEquals("2 + 3 x 4 = 14", MathEval.solveLine("2 + 3 x 4 ="))
        assertEquals("(2+3)*4 = 20", MathEval.solveLine("(2+3)*4"))
        assertEquals("10 ÷ 4 = 2.5", MathEval.solveLine("10 ÷ 4 ="))
        assertEquals("7,5 : 2,5 = 3", MathEval.solveLine("7,5 : 2,5 ="))
        assertEquals("1,5 x 3 = 4,5", MathEval.solveLine("1,5 x 3"))
        assertEquals("2^10 = 1024", MathEval.solveLine("2^10 ="))
        assertEquals("√16 = 4", MathEval.solveLine("√16 ="))
        assertEquals("-3 + 5 = 2", MathEval.solveLine("-3 + 5 ="))
        assertEquals("toplam 12+8 = 20", MathEval.solveLine("toplam 12+8 = 99"))
        assertEquals("1/3 = 0.333333333333", MathEval.solveLine("1/3"))
    }

    @Test fun errors() {
        assertThrows(MathEval.MathError::class.java) { MathEval.solveLine("merhaba") }
        assertThrows(MathEval.MathError::class.java) { MathEval.solveLine("5/0=") }
        assertThrows(MathEval.MathError::class.java) { MathEval.solveLine("(2+3=") }
    }
}
