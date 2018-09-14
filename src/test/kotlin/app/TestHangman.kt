package app

import kotlinx.coroutines.experimental.channels.Channel
import org.junit.Assert.assertEquals
import org.slf4j.LoggerFactory

class TestHangman : Hangman() {
    private val log = LoggerFactory.getLogger(javaClass)

    val input = Channel<String>(1)
    val output = Channel<String>(1)

    override suspend fun printString(str: String) {
        log.info("=>: $str")
        output.send(str)
    }

    override suspend fun readString(): String {
        val str = input.receive()
        log.info("<=: $str")
        return str
    }

    override suspend fun readInt(): Int {
        val i = input.receive().toInt()
        log.info("<=: $i")
        return i
    }

    suspend fun assertRound(expectKnownLetters: String, expectAskLetter: Char, answer: Answer) {
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