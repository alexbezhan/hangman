package app

import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Tests {
    @Test
    fun shouldPickLetterThatDividesWordsInHalfByFrequency() {
        assertEquals(LetterCandidate('b', 3), TestHangman().pickLetter(listOf("abbb", "acba", "abba", "aaaa", "aaaa"), emptyList()))
    }

    @Test
    fun shouldBuildIndex() {
        val index = buildIndex(listOf("god", "gord", "ggod")).index
        assertEquals(4, index.size)
        assertEquals(listOf("god", "gord", "ggod"), index[setOf('g', 'd')])
        assertEquals(listOf("god", "gord", "ggod"), index[setOf('g', 'd', 'o')])
        assertEquals(listOf("gord"), index[setOf('g', 'd', 'r')])
        assertEquals(listOf("gord"), index[setOf('g', 'd', 'o', 'r')])
    }

    @Test
    fun shouldBuildMultipleIndexes() {
        val indexes = IndexBuilder.build(listOf("god", "gord", "color", "colour"))
        assertEquals(2, indexes.size)

        val gd = indexes[FirstLastChar('g', 'd')]!!.index
        assertEquals(4, gd.size)
        assertEquals(listOf("god", "gord"), gd[setOf('g', 'd')])
        assertEquals(listOf("god", "gord"), gd[setOf('g', 'd', 'o')])
        assertEquals(listOf("gord"), gd[setOf('g', 'd', 'r')])
        assertEquals(listOf("gord"), gd[setOf('g', 'd', 'r', 'o')])

        val cr = indexes[FirstLastChar('c', 'r')]!!.index
        assertEquals(8, cr.size)
        assertEquals(listOf("color", "colour"), cr[setOf('c', 'r')])
        assertEquals(listOf("color", "colour"), cr[setOf('c', 'r', 'o')])
        assertEquals(listOf("color", "colour"), cr[setOf('c', 'r', 'l')])
        assertEquals(listOf("color", "colour"), cr[setOf('c', 'r', 'l', 'o')])
        assertEquals(listOf("colour"), cr[setOf('c', 'r', 'u')])
        assertEquals(listOf("colour"), cr[setOf('c', 'r', 'o', 'u')])
        assertEquals(listOf("colour"), cr[setOf('c', 'r', 'l', 'u')])
        assertEquals(listOf("colour"), cr[setOf('c', 'r', 'l', 'o', 'u')])
    }

    @Test
    fun shouldBuildIndexesAndCombineFiles() {
        val dir = Files.createTempDirectory("test-index").toFile().apply { deleteOnExit() }
        IndexBuilder.buildAndPersist(dir, listOf("god", "gord", "color", "colour"), 1, 0, 4)

        val gd = WordIndex.read(dir, FirstLastChar('g', 'd'))!!.index
        println(gd)
        assertEquals(4, gd.size)
        assertEquals(listOf("god", "gord"), gd[setOf('g', 'd')])
        assertEquals(listOf("god", "gord"), gd[setOf('g', 'd', 'o')])
        assertEquals(listOf("gord"), gd[setOf('g', 'd', 'r')])
        assertEquals(listOf("gord"), gd[setOf('g', 'd', 'r', 'o')])

        val cr = WordIndex.read(dir, FirstLastChar('c', 'r'))!!.index
        assertEquals(8, cr.size)
        assertEquals(listOf("color", "colour"), cr[setOf('c', 'r')])
        assertEquals(listOf("color", "colour"), cr[setOf('c', 'r', 'o')])
        assertEquals(listOf("color", "colour"), cr[setOf('c', 'r', 'l')])
        assertEquals(listOf("color", "colour"), cr[setOf('c', 'r', 'l', 'o')])
        assertEquals(listOf("colour"), cr[setOf('c', 'r', 'u')])
        assertEquals(listOf("colour"), cr[setOf('c', 'r', 'o', 'u')])
        assertEquals(listOf("colour"), cr[setOf('c', 'r', 'l', 'u')])
        assertEquals(listOf("colour"), cr[setOf('c', 'r', 'l', 'o', 'u')])
    }

    @Test
    fun shouldBuildIndexFromTestFile() {
        val index = IndexBuilder.build(readTestWords())[FirstLastChar('c', 's')]!!
        assertEquals(listOf("cheque's", "cheques", "colourss", "colour's", "colours"), index[setOf('c', 's')])
        assertEquals(listOf("cheque's", "cheques"), index[setOf('c', 'q', 's')])
        assertEquals(listOf("colourss", "colour's", "colours"), index[setOf('c', 'l', 's')])
    }

    @Test
    fun shouldGuessUsingIndexFromTestFile() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('c', 's', IndexBuilder.build(readTestWords())[FirstLastChar('c', 's')]!!) }
        runBlocking {
            hangman.assertRound("cs", '\'', Answer.Yes(6))
            hangman.assertRound("c's", 'l', Answer.Yes(2))
            hangman.assertWin("colour's")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldGuessIgnoringCase() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('g', 'd', IndexBuilder.build(listOf("GOd", "gord"))[FirstLastChar('g', 'd')]!!) }
        runBlocking {
            hangman.assertRound("gd", 'o', Answer.Yes(1))
            hangman.assertRound("god", 'r', Answer.No)
            hangman.assertWin("GOd")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldGuessLongerWord() {
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('g', 'd', IndexBuilder.build(listOf("god", "gord"))[FirstLastChar('g', 'd')]!!) }
        runBlocking {
            hangman.assertRound("gd", 'o', Answer.Yes(1))
            hangman.assertRound("god", 'r', Answer.Yes(2))
            hangman.assertWin("gord")
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
            IndexBuilder.build(words)[FirstLastChar(words.first().first(), words.first().last())]!!

    private fun readTestWords(): List<String> =
            javaClass.getResource("/test-words/canadian-words.10")?.let { File(it.toURI()) }?.let { file ->
                file.readLines().map { it.trim() }
            } ?: error("words files not found")
}

