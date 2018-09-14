package app

import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        println("Hi. Please, think of a word.")
        println("What are the first and last letters ?")
        val (firstChar, lastChar) = 'a' to 'o'/*Scanner(System.`in`).next().toCharArray() TODO*/

        val knownWords = run {
            val wordsDir = javaClass.getResource("/words")?.let { File(it.toURI()) } ?: error("words dir not found")
            wordsDir.listFiles().flatMap { file ->
                file.readLines().map { it.trim() }
            }
        }

        runBlocking {
            object : Hangman(firstChar, lastChar, listOf("abao", "aabo")/*knownWords TODO*/) {
                override suspend fun printString(str: String) = println(str)
                override suspend fun readString(): String = Scanner(System.`in`).next().trim()
                override suspend fun readInt(): Int = Scanner(System.`in`).nextInt()
            }.start()
        }
    }
}

abstract class Hangman(private val firstChar: Char, private val lastChar: Char, knownWords: List<String>) {
    private val log = LoggerFactory.getLogger(javaClass)

    abstract suspend fun printString(str: String)
    abstract suspend fun readString(): String
    abstract suspend fun readInt(): Int

    private val wordIndex: WordIndex = buildIndex(firstChar, lastChar, knownWords)

    suspend fun start(knownLetters: Set<KnownLetter> = emptySet(), without: Set<Char> = emptySet()) {
        log.debug("[wordIndex : ${wordIndex.index}]")
        log.debug("[knownLetters: $knownLetters]")
        val wordCandidates = wordIndex[knownLetters]?.filter { word -> without.intersect(word.toCharArray().toSet()).isEmpty() }
        if (wordCandidates?.size == 1) {
            printString("It's ${wordCandidates.first()}")
            printString("Bye.")
        } else {
            val word = wordCandidates?.firstOrNull()
            if (word == null) {
                printString("I don't know this word. I give up. You won! Congratulations!")
                printString("Bye.")
            } else {
                log.debug("[word: $word]")
                val letter = word.withIndex().drop(1).dropLast(1).dropWhile { (i, c) -> knownLetters.exists { it.index == i && it.char == c } }.firstOrNull()?.value
                if (letter == null) {
                    printString("It's $word")
                    printString("Bye.")
                } else {
                    printString(firstChar + knownLetters.sortedBy { it.index }.map { it.char }.joinToString("") + lastChar)
                    printString("$letter ? (yes/no)")
                    val answer = readAnswer(knownLetters)
                    when (answer) {
                        is Answer.Yes -> {
                            val idx = answer.index
                            val nextLetters = knownLetters + KnownLetter(idx, letter)
                            start(nextLetters, without)
                        }
                        is Answer.No -> start(knownLetters, without + letter)
                    }
                }
            }
        }
    }

    private suspend fun readAnswer(letters: Set<KnownLetter>): Answer {
        return when (readString()) {
            "yes" -> Answer.Yes(readIdx(letters))
            "no" -> Answer.No
            else -> {
                printString("Wrong answer. Try again")
                readAnswer(letters)
            }
        }
    }

    private tailrec suspend fun readIdx(letters: Set<KnownLetter>): Int {
        printString("Letter idx: ")
        val idx = readInt()
        val alreadyHaveThisIdx = letters.map { it.index }.contains(idx)
        return if (idx < 0 || alreadyHaveThisIdx) {
            printString("Wrong pos")
            readIdx(letters)
        } else {
            idx
        }
    }
}

fun buildIndex(firstChar: Char, lastChar: Char, knownWords: List<String>): WordIndex {
    tailrec fun combinations(word: String, charIdx: Int = 0, acc: List<Set<KnownLetter>> = emptyList()): List<Set<KnownLetter>> {
        val char = word.firstOrNull()
        return if (char == null) {
            acc.map { it.toSet() }
        } else {
            val nextWord = word.drop(1)
            if (nextWord.isEmpty() || charIdx == 0) {
                // skip first and last chars, we don't need them in the index, because we query index without last char
                combinations(nextWord, charIdx + 1, acc)
            } else {
                val letter = KnownLetter(charIdx, char)
                val nextAcc = acc.flatMap { set -> setOf(set, set + letter) }.plusElement(setOf(letter))
                combinations(nextWord, charIdx + 1, nextAcc)
            }
        }
    }

    val index = knownWords.flatMap { word ->
        if (!word.startsWith(firstChar) || !word.endsWith(lastChar)) emptyList()
        else combinations(word).map { it to word }
    }.groupBy { it.first }.mapValues { (_, values) -> values.map { it.second } }
    return WordIndex(index, knownWords)
}

sealed class Answer {
    data class Yes(val index: Int) : Answer()
    object No : Answer()
}

class WordIndex(val index: Map<Set<KnownLetter>, List<String>>, val allWords: List<String>) {
    operator fun get(key: Set<KnownLetter>): List<String>? =
            if (key.isEmpty()) allWords
            else index[key]
}

data class KnownLetter(val index: Int, val char: Char) {
    override fun toString(): String = "$index:$char"
}

fun <T> Iterable<T>.exists(pred: (T) -> Boolean): Boolean = find(pred) != null