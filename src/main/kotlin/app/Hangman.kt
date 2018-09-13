package app

import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

object Main {
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
        runBlocking {
            object : Hangman(0) {
                override suspend fun pushString(str: String) = println(str)
                override suspend fun pullString(): String = Scanner(System.`in`).next().trim()
                override suspend fun pullInt(): Int = Scanner(System.`in`).nextInt()
            }.round(length, knownWords)
        }
    }
}

abstract class Hangman(seed: Long?) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = seed?.let { Random(it) } ?: Random()

    abstract suspend fun pushString(str: String)
    abstract suspend fun pullString(): String
    abstract suspend fun pullInt(): Int

    suspend fun round(wordLength: Int, knownWords: List<String>) {
        val letters = (0 until wordLength).map { null }
        round(knownWords.filter { it.length == wordLength }, letters.withIndex().toList())
    }

    private suspend fun round(knownWords: List<String>, letters: List<IndexedValue<Char?>>, without: List<Char> = emptyList()) {
        log.debug("[knownWords : ${knownWords.joinToString(" ")}]")
        val unknownLetterIdx = letters.find { (_, c) -> c == null }?.index
        if (unknownLetterIdx == null) {
            pushString("I won!")
            pushString("Bye.")
        } else {
            val wordsSubset = run {
                val knownLetters = letters.filter { it.value != null }
                val x = knownWords
                        .filter { word -> knownLetters.forAll { word[it.index] == it.value } }
                        .filter { word -> !without.exists { word.contains(it) } }
                log.debug("[wordsSubset: " + x.joinToString(" ") + "]")
                x
            }
            if (wordsSubset.isEmpty()) {
                pushString("I don't know this word. I give up. You won! Congratulations!")
                pushString("Bye.")
            } else {
                pushString(letters.map { it.value ?: "_" }.joinToString(" "))
                val letter = run {
                    val randomWord = wordsSubset[random.nextInt(wordsSubset.size)]
                    log.debug("[randomWord: $randomWord]")
                    randomWord.toCharArray()[unknownLetterIdx]
                }
                pushString("$letter ? (yes/no)")
                val answer = pullString()
                when (answer) {
                    "yes" -> {
                        val idx = readPos(letters)
                        round(wordsSubset, letters.set(idx, IndexedValue(idx, letter)), without)
                    }
                    "no" -> round(wordsSubset, letters, without + letter)
                    else -> error("Illegal answer: $answer")
                }
            }
        }
    }

    suspend fun readPos(letters: List<IndexedValue<Char?>>): Int {
        pushString("Letter pos [1..${letters.size}]: ")
        val idx = pullInt() - 1
        val alreadyHaveThisIdx = letters.filter { it.value != null }.map { it.index }.contains(idx)
        return if (idx < 0 || idx >= letters.size || alreadyHaveThisIdx) {
            pushString("Wrong pos")
            readPos(letters)
        } else {
            idx
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
