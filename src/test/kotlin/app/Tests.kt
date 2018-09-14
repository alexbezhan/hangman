package app

import app.BuildIndex.buildIndex
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Tests {
    @Test
    fun shouldBuildIndex() {
        assertEquals(
                "{[1:o]=[god, gord], [1:o, 2:r]=[gord], [2:r]=[gord], [1:g]=[ggod], [1:g, 2:o]=[ggod], [2:o]=[ggod]}",
                buildIndex(listOf("god", "gord", "ggod")).index.toString())
    }

    @Test
    fun shouldGuessByPosition() {
        val hangman = TestHangman('a', 'o', listOf("abao", "aabo"))
        val hangmanJob = GlobalScope.launch { hangman.start() }
        runBlocking {
            hangman.assertRound("ao", "b", Answer.Yes(2))
            hangman.assertWin("aabo")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldFilterByExcludedLetters() {
        val hangman = TestHangman('f', 'r', listOf("fdoor", "floor"))
        val hangmanJob = GlobalScope.launch { hangman.start() }
        runBlocking {
            hangman.assertRound("fr", "d", Answer.No)
            hangman.assertWin("floor")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldDropShortWordAndReplaceItWithLonger() {
        val hangman = TestHangman('c', 'r', listOf("center", "centimeter"))
        val hangmanJob = GlobalScope.launch { hangman.start() }
        runBlocking {
            hangman.assertRound("cr", "e", Answer.Yes(6))
            hangman.assertWin("centimeter")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldGiveUpForUnknownWord() {
        val hangman = TestHangman('f', 'r', listOf("floor", "fdoor"))
        val hangmanJob = GlobalScope.launch { hangman.start() }
        runBlocking {
            hangman.assertRound("fr", "l", Answer.Yes(2))
            hangman.assertLose()
            hangmanJob.join()
        }
    }
}