package app

import org.slf4j.LoggerFactory
import java.util.*

abstract class Hangman {
    private val log = LoggerFactory.getLogger(javaClass)

    abstract suspend fun printString(str: String)
    abstract suspend fun readString(): String
    abstract suspend fun readInt(): Int

    suspend fun start(firstChar: Char, lastChar: Char, wordIndex: WordIndex) =
            start(listOf(KnownLetter.create(0, firstChar)), lastChar, wordIndex)

    private suspend fun start(knownLetters: List<KnownLetter>, lastChar: Char, wordIndex: WordIndex, without: Set<Char> = emptySet()) {
        log.debug("[wordIndex : ${wordIndex.index}]")
        log.debug("[knownLetters: $knownLetters]")
        val wordCandidates = wordIndex[knownLetters.asSequence().map { it.char }.toSet() + lastChar]?.asSequence()
                ?.filter { word -> knownLetters.forAll { letter -> word.toLowerCase().getOrNull(letter.index) == letter.char } }
                ?.filter { word -> without.intersect(word.toLowerCase().toCharArray().toSet()).isEmpty() }
                ?.toList()
        log.info("[wordCandidates: ${wordCandidates?.joinToString(" ")}]")
        if (wordCandidates?.size == 1) {
            printString("It's ${wordCandidates.first()}")
            printString("Bye.")
        } else {
            if (wordCandidates == null || wordCandidates.isEmpty()) {
                printString("I don't know this word. I give up. You won! Congratulations!")
                printString("Bye.")
            } else {
                val (letter, _) = pickLetter(wordCandidates, knownLetters)
                printString(knownLetters.asSequence().sortedBy { it.index }.map { it.char }.joinToString("") + lastChar)
                printString("$letter ? (yes/no)")
                val answer = readAnswer(knownLetters)
                when (answer) {
                    is Answer.Yes -> {
                        val idx = answer.index
                        val nextLetters = knownLetters + KnownLetter.create(idx, letter)
                        start(nextLetters, lastChar, wordIndex, without)
                    }
                    is Answer.No -> start(knownLetters, lastChar, wordIndex, without + letter)
                }
            }
        }
    }

    fun pickLetter(words: List<String>, knownLetters: List<KnownLetter>): LetterCandidate {
        val lettersFrequency = words.flatMap { word ->
            word.toLowerCase().toCharArray().dropLast(1).asSequence().withIndex().dropWhile { (i, c) -> knownLetters.exists { it.index == i && it.char == c } }.map { it.value }.toSet()
        }.toList().groupBy { it }.mapValues { (_, values) -> values.size }.toList().sortedBy { it.second }
        // Look for a letter, that is found in half of the words, so the answer to it will reduce our search candidates in half in the next loop.
        // It may be actually done even better, not just look for middle, but find the least distant from words.size/2, but it's ok for now.
        val (middleFrequentLetter, wordsCountWithIt) = lettersFrequency[lettersFrequency.size / 2]
        log.debug("Letter: $middleFrequentLetter, found in $wordsCountWithIt words")
        return LetterCandidate(middleFrequentLetter, wordsCountWithIt)
    }

    private suspend fun readAnswer(letters: Iterable<KnownLetter>): Answer {
        return when (readString()) {
            "yes" -> Answer.Yes(readIdx(letters))
            "no" -> Answer.No
            else -> {
                printString("Wrong answer. Try again")
                readAnswer(letters)
            }
        }
    }

    private tailrec suspend fun readIdx(letters: Iterable<KnownLetter>): Int {
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

data class LetterCandidate(val letter: Char, val wordsCountWithIt: Int)

sealed class Answer {
    data class Yes(val index: Int) : Answer()
    object No : Answer()
}

object KnownLetters {
    val singleLetterCache = PerpetualCache()
    fun create(char: Char): List<Char> {
        val existing = singleLetterCache.get(char)
        return if (existing == null) {
            val new = listOf(char)
            singleLetterCache[char] = new
            new
        } else {
            existing as List<Char>
        }
    }
}

inline class KnownLetter(val arr: Array<Any>)/* : Serializable */ {
    val index: Int
        get() = arr[0] as Int
    val char: Char
        get() = arr[1] as Char

    override fun toString(): String = "$index:$char"

    companion object {
        fun hash(index: Int, char: Char) = Objects.hash(index, char)

        private val cache = PerpetualCache()
        fun create(index: Int, char: Char): KnownLetter {
            val key = hash(index, char)
            val existing = cache.get(key)
            return if (existing == null) {
                val new = KnownLetter(arrayOf(index, char))
                cache[key] = new
                new
            } else {
                existing as KnownLetter
            }
        }
//        private val serialVersionUID: Long = 1
    }
}

data class FirstLastChar(val firstChar: Char, val lastChar: Char)

fun <T> Iterable<T>.forAll(pred: (T) -> Boolean): Boolean = find(not(pred)) == null

fun <T> not(pred: (T) -> Boolean): (T) -> Boolean = { x -> !pred(x) }

fun <T> Iterable<T>.exists(pred: (T) -> Boolean): Boolean = find(pred) != null