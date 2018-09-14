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
        IndexBuilder.buildAndPersist(indexDir, knownWords, 1000)
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
}