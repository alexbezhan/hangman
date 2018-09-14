package app

import app.WordIndex.Companion.indexDir
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

object IndexBuilder {
    private val log = LoggerFactory.getLogger(IndexBuilder::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        if (!indexDir.exists()) {
            indexDir.mkdir()
        }

        log.info("Reading words")
        val knownWords = run {
            val wordsDir = javaClass.getResource("/words")?.let { File(it.toURI()) } ?: error("words dir not found")
            wordsDir.listFiles().flatMap { file ->
                file.readLines().map { it.trim() }
            }
        }
        buildAndPersist(indexDir, knownWords, 100)
    }

    fun buildAndPersist(indexDir: File, knownWords: List<String>, chunkSize: Int) {
        val nThreads = 4
        val tpContext = newFixedThreadPoolContext(nThreads, "tp")

        log.info("Building indexes in chunks by $chunkSize words")
        knownWords.asSequence().chunked(chunkSize).withIndex().chunked(nThreads).map { chunks ->
            val indexes = runBlocking {
                chunks.map { (i, words) ->
                    log.info("chunk #$i start")
                    async(tpContext) { build(words).toList() }
                }.flatMap { it.await() }
            }
            indexes.forEach { (firstLast, index) ->
                val indexFile = WordIndex.indexFile(indexDir, firstLast)
                if (!indexFile.exists())
                    indexFile.createNewFile()

                val indexFromFile = WordIndex.read(indexDir, firstLast)
                val combinedIndex = indexFromFile?.let { index.merge(it) } ?: index
                combinedIndex.writeTo(indexDir, firstLast)
            }
        }.count() // drain the sequence
    }

    fun build(knownWords: List<String>): Map<FirstLastChar, WordIndex> {
        tailrec fun combinations(word: String, firstLast: FirstLastChar, charIdx: Int, acc: List<List<Char>> = emptyList()): Set<Set<Char>> {
            val char = word.firstOrNull()
            return if (char == null) {
                acc.asSequence().map { it.toSet() }.toSet()
            } else {
                val nextWord = word.drop(1)
                if (nextWord.isEmpty() || charIdx == 0) {
                    // skip first and last chars, we don't need them in the index, because we query index without last char
                    combinations(nextWord, firstLast, charIdx + 1, acc)
                } else {
                    val nextAcc = acc.flatMap { list -> listOf(list, list + char) }.plusElement(listOf(firstLast.firstChar, char, firstLast.lastChar))
                    combinations(nextWord, firstLast, charIdx + 1, nextAcc)
                }
            }
        }

        fun combinations(word: String, firstLast: FirstLastChar) =
                combinations(word, firstLast, 0, listOf(listOf(firstLast.firstChar, firstLast.lastChar)))

        val allIndexesMap = mutableMapOf<FirstLastChar, MutableMap<Set<Char>, List<String>>>()
        knownWords.asSequence().groupBy { FirstLastChar(it.first().toLowerCase(), it.last().toLowerCase()) }.map { (firstLast, words) ->
            firstLast to words.flatMap { word -> combinations(word.toLowerCase(), firstLast).map { it to word } }
        }.toList().forEach { (firstLast, combinations) ->
            val indexMap = allIndexesMap[firstLast] ?: mutableMapOf()
            combinations.forEach { (chars, word) ->
                val list = indexMap[chars] ?: emptyList()
                indexMap[chars] = list + word
            }
            allIndexesMap[firstLast] = indexMap
        }
        return allIndexesMap.mapValues { (_, value) -> WordIndex(value.toMap()) }
    }
}
