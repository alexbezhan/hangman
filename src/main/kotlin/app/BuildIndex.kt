package app

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

object BuildIndex {
    private val log = LoggerFactory.getLogger(BuildIndex::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        log.info("Reading words")
        val knownWords = run {
            val wordsDir = javaClass.getResource("/words")?.let { File(it.toURI()) } ?: error("words dir not found")
            wordsDir.listFiles().flatMap { file ->
                file.readLines().map { it.trim() }
            }
        }
        val wordIndex = buildIndex(knownWords.take(1000))
        wordIndex.writeTo(indexFile)
    }

    fun buildIndex(knownWords: List<String>): WordIndex {
        tailrec fun combinations(word: String, firstChar: Char, lastChar: Char, charIdx: Int, acc: List<List<Char>> = emptyList()): Set<Set<Char>> {
            val char = word.firstOrNull()
            return if (char == null) {
                acc.asSequence().map { it.toSet() }.toSet()
            } else {
                val nextWord = word.drop(1)
                if (nextWord.isEmpty() || charIdx == 0) {
                    // skip first and last chars, we don't need them in the index, because we query index without last char
                    combinations(nextWord, firstChar, lastChar, charIdx + 1, acc)
                } else {
                    val nextAcc = acc.flatMap { list -> listOf(list, list + char) }.plusElement(listOf(firstChar, char, lastChar))
                    combinations(nextWord, firstChar, lastChar, charIdx + 1, nextAcc)
                }
            }
        }

        fun combinations(word: String, firstChar: Char, lastChar: Char) =
                combinations(word, firstChar, lastChar, 0, listOf(listOf(firstChar, lastChar)))

        val start = System.nanoTime()
        val nThreads = 4
        val tpContext = newFixedThreadPoolContext(nThreads, "tp")
        return runBlocking {
            log.info("Making combinations")
            val index: Map<Set<Char>, List<String>> = run {
                val accMap = mutableMapOf<Set<Char>, List<String>>()
                knownWords.asSequence().groupBy { it.first().toLowerCase() to it.last().toLowerCase() }.map { (firstLast, words) ->
                    val (firstChar, lastChar) = firstLast
                    log.trace("chunk ${firstChar.toLowerCase()}${lastChar.toLowerCase()} start, words: ${words.size}")
                    firstLast to async(tpContext) {
                        words.map { word -> combinations(word.toLowerCase(), firstChar, lastChar).map { it to word } }
                    }
                }.toList().forEach { (firstLast, deferred) ->
                    val (firstChar, lastChar) = firstLast
                    log.trace("chunk ${firstChar.toLowerCase()}${lastChar.toLowerCase()} end")
                    deferred.await().forEach {
                        it.forEach { (chars, word) ->
                            val list = accMap[chars] ?: emptyList()
                            accMap[chars] = list + word
                        }
                    }
                }
                accMap.toMap()
            }
            log.info("Done in ${(System.nanoTime() - start) / 1000000000}s")
            WordIndex(index, knownWords)
        }
    }

    fun <K, V> Map<K, V>.mergeReduce(other: Map<K, V>, reduce: (V, V) -> V = { a, b -> b }): Map<K, V> {
        val result = LinkedHashMap<K, V>(this.size + other.size)
        result.putAll(this)
        other.forEach { e -> result[e.key] = result[e.key]?.let { reduce(e.value, it) } ?: e.value }
        return result
    }
}

