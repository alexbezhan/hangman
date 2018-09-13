package app

import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

object Main {
    val log = LoggerFactory.getLogger(javaClass)

    @JvmStatic
    fun main(args: Array<String>) {
        fun round(knownWords: List<String>, without: List<Char>, letters: List<IndexedValue<Char?>>) {
            println(letters.map { it.value ?: "_" }.joinToString(" "))
            log.debug("[knownWords : ${knownWords.joinToString(" ")}]")
            val unknownLetterIdx = letters.find { (_, c) -> c == null }?.index
            if (unknownLetterIdx == null) {
                println("done")
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
                    println("I don't know this word. I give up. You won, congratulations!")
                    return
                }

                val letter = run {
                    val randomWord = wordsSubset[Random().nextInt(wordsSubset.size)]
                    log.debug("[randomWord: $randomWord]")
                    randomWord.toCharArray()[unknownLetterIdx]
                }
                println("$letter ? (yes/no)")
                when (Scanner(System.`in`).next().trim()) {
                    "yes" -> {
                        val idx = run {
                            tailrec fun readPos(): Int {
                                print("Letter pos [1..${letters.size}]: ")
                                val idx = Scanner(System.`in`).nextInt() - 1
                                val alreadyHaveThisIdx = letters.filter { it.value != null }.map { it.index }.contains(idx)
                                return if (idx < 0 || idx >= letters.size || alreadyHaveThisIdx) {
                                    println("Wrong pos")
                                    readPos()
                                } else {
                                    idx
                                }
                            }
                            readPos()
                        }
                        round(wordsSubset, without, letters.set(idx, IndexedValue(idx, letter)))
                    }
                    "no" -> round(wordsSubset, without + letter, letters)
                    else -> error("Illegal answer")
                }
            }
        }

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
        round(knownWords.filter { word -> word.length == length }, emptyList(), letters.withIndex().toList())
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
