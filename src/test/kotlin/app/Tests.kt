package app

import app.BuildIndex.buildIndexes
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class Tests {
    @Test
    fun shouldPickLetterThatDividesWordsInHalfByFrequency() {
        assertEquals(LetterCandidate('a', 2), TestHangman().pickLetter(listOf("abbb", "acba", "abba", "aaaa", "aaaa")))
    }

    @Test
    fun shouldBuildIndex() {
        assertEquals(
                "{[g, d]=[god, gord, ggod], [g, d, o]=[god, gord, ggod], [g, d, r]=[gord], [g, d, o, r]=[gord]}",
                buildIndex(listOf("god", "gord", "ggod")).index.toString())
    }

    @Test
    fun shouldBuildMultipleIndexes() {
        val indexes = buildIndexes(listOf("god", "gord", "color", "colour"))
        assertEquals(2, indexes.size)
        assertEquals("{[g, d]=[god, gord], [g, d, o]=[god, gord], [g, d, r]=[gord], [g, d, o, r]=[gord]}", indexes[FirstLastChar('g', 'd')]!!.index.toString())
        assertEquals("{[c, r]=[color, colour], [c, r, o]=[color, colour], [c, r, l]=[color, colour], [c, r, l, o]=[color, colour], [c, r, u]=[colour], [c, r, o, u]=[colour], [c, r, l, u]=[colour], [c, r, l, o, u]=[colour]}", indexes[FirstLastChar('c','r')]!!.index.toString())
    }

    @Test
    fun shouldBuildIndexFromRealFile() {
        val index = buildIndexes(readTestWords())[FirstLastChar('c', 's')]!!
        assertEquals(listOf("cheque's", "cheques", "colourss", "colour's", "colours"), index[setOf('c', 's')])
        assertEquals(listOf("cheque's", "cheques"), index[setOf('c', 'q', 's')])
        assertEquals(listOf("colourss", "colour's", "colours"), index[setOf('c', 'l', 's')])
    }

    @Test
    fun shouldGuessUsingIndexFromRealFile() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch {
            hangman.start('c', 's', buildIndexes(readTestWords())[FirstLastChar('c','s')]!!)
        }
        runBlocking {
            hangman.assertRound("cs", '\'', Answer.Yes(6))
            hangman.assertRound("c's", 'l', Answer.Yes(2))
            hangman.assertWin("colour's")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldGuessByPosition() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('a', 'o', buildIndex(listOf("abao", "aabo"))) }
        runBlocking {
            hangman.assertRound("ao", 'a', Answer.Yes(1))
            hangman.assertWin("aabo")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldFilterByExcludedLetters() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('f', 'r', buildIndex(listOf("fdoor", "floor"))) }
        runBlocking {
            hangman.assertRound("fr", 'l', Answer.No)
            hangman.assertWin("fdoor")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldDropShortWordAndReplaceItWithLonger() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('c', 'r', buildIndex(listOf("center", "centimeter"))) }
        runBlocking {
            hangman.assertRound("cr", 'e', Answer.Yes(6))
            hangman.assertWin("centimeter")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldGiveUpForUnknownWord() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('f', 'r', buildIndex(listOf("floor", "fdoor"))) }
        runBlocking {
            hangman.assertRound("fr", 'd', Answer.Yes(2))
            hangman.assertLose()
            hangmanJob.join()
        }
    }

    private fun buildIndex(words: List<String>): WordIndex =
            buildIndexes(words)[FirstLastChar(words.first().first(), words.first().last())]!!

    private fun readTestWords(): List<String> =
            javaClass.getResource("/test-words/canadian-words.10")?.let { File(it.toURI()) }?.let { file ->
                file.readLines().map { it.trim() }
            } ?: error("words files not found")
}

