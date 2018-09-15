package app

import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files

class IntegrationTests {
    @Test
    fun shouldBuildAndGuessFromRealFiles() {
        val knownWords = run {
            val wordsDir = javaClass.getResource("/words")?.let { File(it.toURI()) } ?: error("words dir not found")
            wordsDir.listFiles().take(10).flatMap { file ->
                file.readLines().map { it.trim() }
            }
        }
        val indexDir = Files.createTempDirectory("test-index").toFile().apply { deleteOnExit() }
        IndexBuilder.buildAndPersist(indexDir, knownWords, 1000, 0,1000 )
        val wordIndex = WordIndex.read(indexDir, FirstLastChar('b', 'd'))!!
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('b', 'd', wordIndex) }
        runBlocking {
            hangman.assertRound("bd", 'a', Answer.Yes(1))
            hangman.assertRound("bad", 'e', Answer.Yes(8))
            hangman.assertWin("Balkanized")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldGuessTheWordPickingLettersWithLeastDistance() {
        val firstLast = FirstLastChar('b', 'n')
        val wordIndex = WordIndex.read(IndexBuilder.indexDir, firstLast)!!
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('b', 'n', wordIndex) }
        runBlocking {
            hangman.assertRound("bn", 'e', Answer.Yes(4))
            hangman.assertRound("ben", 'o', Answer.No)
            hangman.assertRound("ben", 'r', Answer.Yes(2))
            hangman.assertRound("bren", 'i', Answer.Yes(6))
            hangman.assertRound("brein", 'b', Answer.Yes(3))
            hangman.assertRound("brbein", 'a', Answer.Yes(7))
            hangman.assertWin("Berberian")
            hangmanJob.join()
        }
    }

    @Test
    fun shouldGuessTheWordPickingLettersWithLeastDistance2() {
        val firstLast = FirstLastChar('c', 'r')
        val wordIndex = WordIndex.read(IndexBuilder.indexDir, firstLast)!!
        val hangman = TestHangman()
        val hangmanJob = GlobalScope.launch { hangman.start('c', 'r', wordIndex) }
        runBlocking {
            hangman.assertRound("cr", 'a', Answer.No)
            hangman.assertRound("cr", 'i', Answer.No)
            hangman.assertRound("cr", 'r', Answer.No)
            hangman.assertRound("cr", 'l', Answer.No)
            hangman.assertRound("cr", 'n', Answer.Yes(2))
            hangman.assertRound("cnr", 't', Answer.Yes(3))
            hangman.assertRound("cntr", 'm', Answer.No)
            hangman.assertRound("cntr", 'o', Answer.No)
            hangman.assertRound("cntr", 'g', Answer.No)
            hangman.assertRound("cntr", 'e', Answer.Yes(1))
            hangman.assertRound("centr", 'e', Answer.Yes(4))
            hangman.assertWin("center")
            hangmanJob.join()
        }
    }
}