package app

import kotlinx.coroutines.experimental.*
import org.slf4j.LoggerFactory
import java.io.File

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
        val wordIndex = buildIndex(knownWords.take(10000))
        wordIndex.writeTo(indexFile)
    }

    fun buildIndex(knownWords: List<String>): WordIndex {
        val start = System.nanoTime()

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
                    val letter = KnownLetter.create(charIdx, char)
                    val nextAcc = acc.flatMap { set -> setOf(set, set + letter) }.plusElement(KnownLetters.create(letter))
                    combinations(nextWord, charIdx + 1, nextAcc)
                }
            }
        }

        val tpContext = newFixedThreadPoolContext(4, "tp")
        return runBlocking {
            with(CoroutineScope(tpContext)) {
                log.info("Making combinations")
                val combs: List<Pair<Set<KnownLetter>, String>> = run {
                    val accList = mutableListOf<Pair<Set<KnownLetter>, String>>()
                    knownWords.chunked(knownWords.size / 4).map { words ->
                        async { words.map { word -> combinations(word).map { it to word } }}
                    }.forEach{
                        accList.addAll(it.await().flatten())
                    }
                    accList.toList()
                }
//                val combs: List<Pair<Set<KnownLetter, String>> = knownWords.map { word -> async { combinations(word).map { it to word } } }.flatMap { it.await() }
                log.info("Grouping")
                val grouped = run {
                    val accMap = mutableMapOf<Set<KnownLetter>, List<Pair<Set<KnownLetter>, String>>>()
                    combs.chunked(combs.size / 4) { list ->
                        async { list.groupBy { it.first } }
                    }.forEach {
                        accMap.putAll(it.await())
                    }
                    accMap.toMap()
                }
//                val grouped: Map<Set<KnownLetter, List<Pair<Set<KnownLetter, String>>> = combs.groupBy { it.first }
                log.info("Reducing")
                val reduced = grouped.mapValues { (_, values) -> /*printThrottled("."); */values.map { it.second } }
                log.info("Done in ${(System.nanoTime() - start) / 1000000000}s")
                WordIndex(reduced, knownWords)
            }
        }
    }
}

