package app

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
                buildIndex( 'g', 'd', listOf("god", "gord", "ggod")).index.toString())
    }

    @Test
    fun shouldGuessByPosition() {
        val hangman = TestHangman('a','o', listOf("abao", "aabo"))
        val hangmanJob = GlobalScope.launch { hangman.round() }
        runBlocking {
            hangman.assertRound("ao", "b", Answer.Yes(2))
            hangman.assertRound("abo", "a", Answer.Yes(1))
            hangman.assertWin("aabo")
            hangmanJob.join()
        }
    }

    // shouldFilterByExcludedLetters()

    // should drop short word and replace it with longer
    // knownWords: center, centimeter
    // word: center
    // e yes
    // index 6
    // centimeter

    @Test
    fun shouldGiveUpForUnknownWord() {
        val hangman = TestHangman('f','r', listOf("floor", "fdoor"))
        val hangmanJob = GlobalScope.launch { hangman.round() }
        runBlocking {
            hangman.assertRound("fr", "l", Answer.Yes(2))
            hangman.assertLose("flr")
            hangmanJob.join()
        }
    }
}