package app

import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.util.*

object Main {
    val log = LoggerFactory.getLogger(javaClass)

    @JvmStatic
    fun main(args: Array<String>) {
        println("Hi. Please, think of a word.")
        println("What is the word length ?")
        val knownWords = run {
            val wordsDir = javaClass.getResource("/words")?.let { File(it.toURI()) } ?: error("words dir not found")
            wordsDir.listFiles().flatMap { file ->
                file.readLines()
            }
        }

        val length = Scanner(System.`in`).nextInt()
        val letters = (0 until length).map { null }
        Hangman(0, System.out, System.`in`).round(knownWords.filter { word -> word.length == length }, letters.withIndex().toList())
    }
}

class Hangman(seed: Long?, private val printStream: PrintStream, private val inputStream: InputStream) {
    private val random = seed?.let { Random(it) } ?: Random()

    fun round(knownWords: List<String>, letters: List<IndexedValue<Char?>>, without: List<Char> = emptyList()) {
        println(letters.map { it.value ?: "_" }.joinToString(" "))
        Main.log.debug("[knownWords : ${knownWords.joinToString(" ")}]")
        val unknownLetterIdx = letters.find { (_, c) -> c == null }?.index
        if (unknownLetterIdx == null) {
            printStream.println("done")
        } else {
            val wordsSubset = run {
                val knownLetters = letters.filter { it.value != null }
                val x = knownWords
                        .filter { word -> knownLetters.forAll { word[it.index] == it.value } }
                        .filter { word -> !without.exists { word.contains(it) } }
                Main.log.debug("[wordsSubset: " + x.joinToString(" ") + "]")
                x
            }
            if (wordsSubset.isEmpty()) {
                printStream.println("I don't know this word. I give up. You won, congratulations!")
                return
            }

            val letter = run {
                val randomWord = wordsSubset[random.nextInt(wordsSubset.size)]
                Main.log.debug("[randomWord: $randomWord]")
                randomWord.toCharArray()[unknownLetterIdx]
            }
            printStream.println("$letter ? (yes/no)")
            when (Scanner(inputStream).next().trim()) {
                "yes" -> {
                    val idx = run {
                        tailrec fun readPos(): Int {
                            print("Letter pos [1..${letters.size}]: ")
                            val idx = Scanner(inputStream).nextInt() - 1
                            val alreadyHaveThisIdx = letters.filter { it.value != null }.map { it.index }.contains(idx)
                            return if (idx < 0 || idx >= letters.size || alreadyHaveThisIdx) {
                                printStream.println("Wrong pos")
                                readPos()
                            } else {
                                idx
                            }
                        }
                        readPos()
                    }
                    round(wordsSubset, letters.set(idx, IndexedValue(idx, letter)), without)
                }
                "no" -> round(wordsSubset, letters, without + letter)
                else -> error("Illegal answer")
            }
        }
    }
}

fun <T> Iterable<IndexedValue<T>>.forAll(pred: (IndexedValue<T>) -> Boolean): Boolean = find(not(pred)) == null

fun <T> not(pred: (T) -> Boolean): (T) -> Boolean = { x -> !pred(x) }

fun <T> List<T>.exists(pred: (T) -> Boolean): Boolean = find(pred) != null

fun <T> List<T>.set(idx: Int, e: T): List<T> {
    val mut = toMutableList()
    mut[idx] = e
    return mut.toList()
}
