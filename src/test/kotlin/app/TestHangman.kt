package app

import kotlinx.coroutines.experimental.channels.Channel
import org.junit.Assert.assertEquals
import org.slf4j.LoggerFactory

class TestHangman(firstChar: Char, lastChar: Char, knownWords: List<String>) : Hangman(firstChar, lastChar, knownWords) {
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
        input.send(answer.javaClass.simpleName.toLowerCase())
        if (answer is Answer.Yes) {
            assertEquals("Letter idx: ", output.receive())
            input.send(answer.index.toString())
        }
    }

    suspend fun assertLose() {
        assertEquals("I don't know this word. I give up. You won! Congratulations!", output.receive())
        assertEquals("Bye.", output.receive())
    }

    suspend fun assertWin(word: String) {
        assertEquals("It's $word", output.receive())
        assertEquals("Bye.", output.receive())
    }
}