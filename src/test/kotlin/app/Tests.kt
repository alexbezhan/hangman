package app

import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.LoggerFactory

class Tests {
    @Test
    fun shouldWinSimpleGame() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch {
            hangman.round(5, listOf("among", "color", "favor", "labor", "rumor"))
        }
        runBlocking {
            hangman.assertRound("_ _ _ _ _", "a", Answer.Yes(1))
            hangman.assertRound("a _ _ _ _", "m", Answer.Yes(2))
            hangman.assertRound("a m _ _ _", "o", Answer.Yes(3))
            hangman.assertRound("a m o _ _", "n", Answer.Yes(4))
            hangman.assertRound("a m o n _", "g", Answer.Yes(5))
            hangman.assertWin()
            hangmanJob.join()
        }
    }

    @Test
    fun shouldFilterByExcludedLetters() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch {
            hangman.round(3, listOf("aaa", "abb", "bbb"))
        }
        runBlocking {
            hangman.assertRound("_ _ _", "a", Answer.No)
            hangman.assertRound("_ _ _", "b", Answer.Yes(1))
            hangman.assertRound("b _ _", "b", Answer.Yes(2))
            hangman.assertRound("b b _", "b", Answer.Yes(3))
            hangman.assertWin()
            hangmanJob.join()
        }
    }

    @Test
    fun shouldFilterWordsByLength() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch {
            hangman.round(5, listOf("amon", "among", "amongg"))
        }
        runBlocking {
            hangman.assertRound("_ _ _ _ _", "a", Answer.Yes(1))
            hangman.assertRound("a _ _ _ _", "m", Answer.Yes(2))
            hangman.assertRound("a m _ _ _", "o", Answer.Yes(3))
            hangman.assertRound("a m o _ _", "n", Answer.Yes(4))
            hangman.assertRound("a m o n _", "g", Answer.Yes(5))
            hangman.assertWin()
            hangmanJob.join()
        }
    }

    @Test
    fun shouldGiveUpForUnknownWord() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch {
            hangman.round(5, listOf("floor"))
        }
        runBlocking {
            hangman.assertRound("_ _ _ _ _", "f", Answer.No)
            hangman.assertLose()
            hangmanJob.join()
        }
    }
}

class TestHangman : Hangman(0) {
    private val log = LoggerFactory.getLogger(javaClass)

    val input = Channel<String>(1)
    val output = Channel<String>(1)

    override suspend fun pushString(str: String) {
        log.debug("output: $str")
        output.send(str)
    }

    override suspend fun pullString(): String {
        val str = input.receive()
        log.debug("input: $str")
        return str
    }

    override suspend fun pullInt(): Int {
        val i = input.receive().toInt()
        log.debug("input: $i")
        return i
    }

    suspend fun assertRound(expectKnownLetters: String, expectAskLetter: String, answer: Answer) {
        assertEquals(expectKnownLetters, output.receive())
        assertEquals("$expectAskLetter ? (yes/no)", output.receive())
        input.send(answer.name)
        if (answer is Answer.Yes) {
            assertEquals("Letter pos [1..${expectKnownLetters.replace(" ", "").length}]: ", output.receive())
            input.send(answer.pos.toString())
        }
    }

    suspend fun assertLose() {
        assertEquals("I don't know this word. I give up. You won! Congratulations!", output.receive())
        assertEquals("Bye.", output.receive())
    }
    suspend fun assertWin() {
        assertEquals("I won!", output.receive())
        assertEquals("Bye.", output.receive())
    }
}


sealed class Answer(val name: String) {
    data class Yes(val pos: Int) : Answer("yes")
    object No : Answer("no")
}