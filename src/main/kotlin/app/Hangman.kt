package app

import com.sun.corba.se.impl.util.RepositoryId.cache
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicLong

abstract class Hangman(private val firstChar: Char, private val lastChar: Char, private val wordIndex: WordIndex) {
    private val log = LoggerFactory.getLogger(javaClass)

    abstract suspend fun printString(str: String)
    abstract suspend fun readString(): String
    abstract suspend fun readInt(): Int

    suspend fun start(knownLetters: Set<KnownLetter> = emptySet(), without: Set<Char> = emptySet()) {
        log.info("[wordIndex : ${wordIndex.index}]")
        log.info("[knownLetters: $knownLetters]")
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
                log.info("[word: $word]")
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
                            val nextLetters = knownLetters + KnownLetter.create(idx, letter)
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

sealed class Answer {
    data class Yes(val index: Int) : Answer()
    object No : Answer()
}

object KnownLetters {
    private val tick = AtomicLong()
    val singleLetterCache = PerpetualCache()
    fun create(letter: KnownLetter): Set<KnownLetter> {
        val tickValue = tick.incrementAndGet()
        if (tickValue % 300 == 0L) {
            println("KnownLetters cache: " + singleLetterCache.size)
        }
        val key = KnownLetter.hash(letter.index, letter.char)
        val existing = singleLetterCache.get(key)
        return if (existing == null) {
            val new = setOf(letter)
            singleLetterCache[key] = new
            new
        } else {
            existing as Set<KnownLetter>
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

        private val tick = AtomicLong()
        private val cache = PerpetualCache()
        fun create(index: Int, char: Char): KnownLetter {
            val tickValue = tick.incrementAndGet()
            if (tickValue % 300 == 0L) {
                println("KnownLetter cache: " + cache.size)
            }
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

fun <T> Iterable<T>.exists(pred: (T) -> Boolean): Boolean = find(pred) != null