package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaNamesTest {
    private fun names(vararg pairs: Pair<String, Double?>) = object : Formula.Names {
        val map = pairs.toMap()
        override fun known(name: String) = name in map
        override fun value(name: String) = map[name]
    }

    @Test
    fun namesFunctionsAndComparisonsWork() {
        val n = names("maf" to 10.0, "speed" to 50.0)
        assertEquals(10.0 * 3600 / 15.5 / 540, Formula.evaluate("maf * 3600 / 15.5 / 540", null, n).value!!, 1e-9)
        assertEquals(1.0, Formula.evaluate("speed > 5", null, n).value!!, 0.0)
        assertEquals(0.0, Formula.evaluate("speed <= 5", null, n).value!!, 0.0)
        assertEquals(10.0, Formula.evaluate("min(maf, speed, 99)", null, n).value!!, 0.0)
        assertEquals(50.0, Formula.evaluate("MAX(maf, Speed)", null, n).value!!, 0.0)
        assertEquals(3.0, Formula.evaluate("abs(-3)", null, n).value!!, 0.0)
        assertEquals(4.0, Formula.evaluate("round(3.6)", null, n).value!!, 0.0)
    }

    @Test
    fun ifOnlyWorksOutTheBranchTaken() {
        val stopped = names("use" to 1.2, "speed" to 0.0)
        assertEquals(0.0, Formula.evaluate("if(speed > 5, use / speed * 100, 0)", null, stopped).value!!, 0.0)
        val moving = names("use" to 1.2, "speed" to 60.0)
        assertEquals(2.0, Formula.evaluate("if(speed > 5, use / speed * 100, 0)", null, moving).value!!, 1e-9)
    }

    @Test
    fun bytesBitsAndSignsInAnswers() {
        val none = names()
        assertEquals(1.0, Formula.evaluate("bit(A, 2)", listOf(0b0100), none).value!!, 0.0)
        assertEquals(0.0, Formula.evaluate("bit(A, 1)", listOf(0b0100), none).value!!, 0.0)
        assertEquals(-2.0, Formula.evaluate("signed(A)", listOf(0xFE), none).value!!, 0.0)
        assertEquals(-1.0, Formula.evaluate("signed16(A*256+B)", listOf(0xFF, 0xFF), none).value!!, 0.0)
        assertEquals(127.0, Formula.evaluate("signed(A)", listOf(0x7F), none).value!!, 0.0)
    }

    @Test
    fun problemsAreToldApart() {
        val n = names("maf" to null)
        assertEquals(Formula.Problem.UNKNOWN, Formula.evaluate("mfa * 2", null, n).problem)
        assertEquals("mfa", Formula.evaluate("mfa * 2", null, n).name)
        assertEquals(Formula.Problem.NO_VALUE, Formula.evaluate("maf * 2", null, n).problem)
        assertEquals(Formula.Problem.SYNTAX, Formula.evaluate("maf *", null, n).problem)
        assertEquals(Formula.Problem.SYNTAX, Formula.evaluate("nope(1)", null, n).problem)
        assertEquals(Formula.Problem.SYNTAX, Formula.evaluate("abs(1, 2)", null, n).problem)
        assertEquals(Formula.Problem.MATH, Formula.evaluate("1 / 0", null, n).problem)
        // A to H are bytes only in a request's formula.
        assertEquals(Formula.Problem.UNKNOWN, Formula.evaluate("A * 2", null, n).problem)
    }

    @Test
    fun namesAndBytesAreListed() {
        assertEquals(setOf("maf", "speed"), Formula.names("if(speed > 5, maf / speed, 0) + A"))
        assertNull(Formula.names("(maf"))
        assertTrue(Formula.usesBytes("(A*256+B)/100"))
        assertFalse(Formula.usesBytes("maf / 100"))
    }
}

class MyReadingRulesTest {
    private fun pid(key: String, request: String, formula: String = "A", header: String? = null, reply: String? = null, session: String? = null) =
        MyReading(key, key, "", MyReadingKind.PID, formula, request, header, reply, session)

    private fun calc(key: String, formula: String, kind: MyReadingKind = MyReadingKind.FORMULA) = MyReading(key, key, "", kind, formula)

    @Test
    fun keysComeFromTheName() {
        assertEquals("lpg_use", MyReadingRules.keyFor("LPG use", emptySet()))
        assertEquals("lpg_use_2", MyReadingRules.keyFor("LPG use", setOf("lpg_use")))
        assertEquals("r_100", MyReadingRules.keyFor("100", emptySet()))
        assertEquals("my_speed", MyReadingRules.keyFor("Speed", emptySet()))
        assertEquals("my_a", MyReadingRules.keyFor("A", emptySet()))
    }

    @Test
    fun onlyReadRequestsAreKept() {
        assertNull(MyReadingRules.problem(pid("maf", "0110"), emptyList()))
        assertNull(MyReadingRules.problem(pid("soot", "22F40C", header = "6A8", reply = "688", session = "10C0"), emptyList()))
        assertEquals(MyReadingProblem.BAD_REQUEST, MyReadingRules.problem(pid("x", "04"), emptyList()))
        assertEquals(MyReadingProblem.BAD_REQUEST, MyReadingRules.problem(pid("x", "2E1234"), emptyList()))
        assertEquals(MyReadingProblem.BAD_REQUEST, MyReadingRules.problem(pid("x", "011"), emptyList()))
        assertEquals(MyReadingProblem.BAD_SESSION, MyReadingRules.problem(pid("x", "22F40C", header = "6A8", session = "1002"), emptyList()))
        assertEquals(MyReadingProblem.BAD_SESSION, MyReadingRules.problem(pid("x", "22F40C", session = "10C0"), emptyList()))
        assertEquals(MyReadingProblem.BAD_ADDRESS, MyReadingRules.problem(pid("x", "0110", header = "7E"), emptyList()))
        assertEquals(MyReadingProblem.BAD_ADDRESS, MyReadingRules.problem(pid("x", "0110", reply = "7E8"), emptyList()))
    }

    @Test
    fun keysAndFormulasAreChecked() {
        val maf = pid("maf", "0110")
        assertEquals(MyReadingProblem.KEY_TAKEN, MyReadingRules.problem(calc("maf", "1"), listOf(maf)))
        assertEquals(MyReadingProblem.BAD_KEY, MyReadingRules.problem(calc("speed", "1"), emptyList()))
        assertEquals(MyReadingProblem.BAD_KEY, MyReadingRules.problem(calc("b", "1"), emptyList()))
        assertEquals(MyReadingProblem.BAD_KEY, MyReadingRules.problem(calc("Lpg use", "1"), emptyList()))
        assertEquals(MyReadingProblem.BAD_FORMULA, MyReadingRules.problem(calc("x", "maf *"), listOf(maf)))
        assertEquals(MyReadingProblem.NO_BYTES, MyReadingRules.problem(calc("x", "A * 2"), listOf(maf)))
        assertEquals(listOf("mfa"), MyReadingRules.unknownNames("mfa + speed + maf", listOf(maf)))
    }

    @Test
    fun valuesFollowEachOtherInAnyOrder() {
        val list = listOf(
            calc("l100", "if(speed > 5, lh / speed * 100, 0)"),
            calc("lh", "maf * 3600 / 15.5 / 540"),
            pid("maf", "0110"),
            calc("used", "lh", MyReadingKind.TOTAL)
        )
        val v = MyReadingRules.values(list, mapOf("speed" to 60.0), mapOf("maf" to 15.5), mapOf("used" to 2.5))
        val lh = 15.5 * 3600 / 15.5 / 540
        assertEquals(lh, v["lh"]!!, 1e-9)
        assertEquals(lh / 60 * 100, v["l100"]!!, 1e-9)
        assertEquals(2.5, v["used"]!!, 0.0)
        assertEquals(lh, MyReadingRules.rates(list, mapOf("speed" to 60.0), v)["used"]!!, 1e-9)
    }

    @Test
    fun aLoopGivesNoValueRatherThanHanging() {
        val list = listOf(calc("a1", "b1 + 1"), calc("b1", "a1 + 1"), calc("c1", "2"))
        val v = MyReadingRules.values(list, emptyMap(), emptyMap(), emptyMap())
        assertNull(v["a1"])
        assertNull(v["b1"])
        assertEquals(2.0, v["c1"]!!, 0.0)
    }

    @Test
    fun noAnswerNoValue() {
        assertNull(MyReadingRules.values(listOf(pid("maf", "0110")), emptyMap(), emptyMap(), emptyMap())["maf"])
        val list = listOf(pid("maf", "0110"), calc("lh", "maf * 2"))
        assertNull(MyReadingRules.values(list, emptyMap(), emptyMap(), emptyMap())["lh"])
    }

    @Test
    fun answersAreReadWithTheirFormula() {
        val maf = pid("maf", "0110", "(A*256+B)/100")
        assertEquals((0x01 * 256 + 0xF4) / 100.0, MyReadingRules.read(maf, "41 10 01 F4").value!!, 1e-9)
        // Headers on, no spaces, the way this car's adapter answers.
        assertEquals((0x01 * 256 + 0xF4) / 100.0, MyReadingRules.read(maf, "7E8044110 01F4").value!!, 1e-9)
        assertNull(MyReadingRules.read(maf, "NO DATA").value)
    }

    @Test
    fun theExamplesAreValidAndChained() {
        val ex = MyReadingRules.examples(listOf("Air flow", "LPG use", "LPG used", "LPG per 100 km"), emptySet())
        assertEquals(4, ex.size)
        ex.forEachIndexed { i, r -> assertNull(r.key, MyReadingRules.problem(r, ex.take(i))) }
        ex.forEach { assertTrue(MyReadingRules.unknownNames(it.formula, ex).isEmpty() || it.kind == MyReadingKind.PID) }
        val v = MyReadingRules.values(ex, mapOf("speed" to 100.0), mapOf(ex[0].key to 20.0), emptyMap())
        assertEquals(20.0 * 3600 / 15.5 / 540, v[ex[1].key]!!, 1e-9)
    }
}
