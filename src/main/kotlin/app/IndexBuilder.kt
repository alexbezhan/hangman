package app

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object IndexBuilder {
    private val log = LoggerFactory.getLogger(IndexBuilder::class.java)
    val indexDir = File("/Users/alex/source/hangman/.word-index")

    @JvmStatic
    fun main(args: Array<String>) {
        if (!indexDir.exists()) {
            indexDir.mkdir()
        }

        log.info("Reading words")
        val knownWords = run {
            val wordsDir = File("/Users/alex/source/hangman/src/main/resources/words")
            wordsDir.listFiles().flatMap { file ->
                file.readLines().map { it.trim() }
            }
        }
        val chunkSize = args[0].toInt()
        val drop = args[1].toInt()
        val take = args[2].toInt()
        log.info("chunk size: $chunkSize, drop: $drop, take: $take")
        buildAndPersist(indexDir, knownWords, chunkSize, drop, take)
    }

    fun buildAndPersist(indexDir: File, knownWords: List<String>, chunkSize: Int, drop: Int = 0, take: Int) {
        val nThreads = 4
        val tpContext = newFixedThreadPoolContext(nThreads, "tp")

        knownWords.asSequence().drop(drop).take(take).chunked(chunkSize).withIndex().chunked(nThreads).map { chunks ->
            val indexes = runBlocking {
                chunks.map { (i, words) ->
                    log.info("chunk #$i start")
                    async(tpContext) { build(words).toList() }
                }.map { it.await() } // don't flatMap here, to save the memory
            }
            indexes.forEach { indexes ->
                indexes.forEach { (firstLast, index) ->
                    val indexFile = WordIndex.indexFile(indexDir, firstLast)
                    if (!indexFile.exists())
                        indexFile.createNewFile()

                    val indexFromFile = WordIndex.read(indexDir, firstLast)
                    val combinedIndex = indexFromFile?.let { index.merge(it) } ?: index
                    combinedIndex.writeTo(indexDir, firstLast)
                }
            }
        }.count() // drain the sequence
    }

    fun build(knownWords: List<String>): Map<FirstLastChar, WordIndex> {
        fun combinations(word: String, firstLast: FirstLastChar): Set<Set<Char>> {
            var mutAcc = mutableListOf<List<Char>>()
            val chars = word.toCharArray()
            chars.withIndex().forEach { (charIdx, char) ->
                // skip first and last chars, we don't need them alone in the index
                if (charIdx > 0 && charIdx < chars.size) {
                    val nextMutAcc = mutableListOf<List<Char>>()
                    mutAcc.forEach { list ->
                        nextMutAcc.add(list)
                        nextMutAcc.add(list + char)
                    }
                    nextMutAcc.add(CharsCache.create(firstLast.firstChar, char, firstLast.lastChar))
                    mutAcc = nextMutAcc
                }
            }
            return mutAcc.asSequence().map { it.toSet() }.toSet()
        }

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

private object CharsCache {
    private val singleLetterCache = SoftCache(PerpetualCache())

    // don't use varargs, we don't wanna allocate redundant array here
    fun create(c1: Char, c2: Char, c3: Char): List<Char> {
        val key = Objects.hash(c1, c2, c3)
        val existing = singleLetterCache.get(key)
        return if (existing == null) {
            val new = listOf(c1, c2, c3)
            singleLetterCache[key] = new
            new
        } else {
            existing as List<Char>
        }
    }
}

private interface Cache {
    val size: Int

    operator fun set(key: Any, value: Any)

    operator fun get(key: Any): Any?

    fun remove(key: Any): Any?

    fun clear()
}

private class PerpetualCache : Cache {
    private val cache = ConcurrentHashMap<Any, Any>()

    override val size: Int
        get() = cache.size

    override fun set(key: Any, value: Any) {
        this.cache[key] = value
    }

    override fun remove(key: Any) = this.cache.remove(key)

    override fun get(key: Any) = this.cache[key]

    override fun clear() = this.cache.clear()
}

private class SoftCache(private val delegate: Cache) : Cache {
    private val referenceQueue = ReferenceQueue<Any>()

    private class SoftEntry internal constructor(
            internal val key: Any,
            value: Any,
            referenceQueue: ReferenceQueue<Any>) : SoftReference<Any>(value, referenceQueue)

    override val size: Int
        get() = delegate.size

    override fun set(key: Any, value: Any) {
        removeUnreachableItems()
        val softEntry = SoftEntry(key, value, referenceQueue)
        delegate[key] = softEntry
    }

    override fun remove(key: Any) {
        delegate.remove(key)
        removeUnreachableItems()
    }

    override fun get(key: Any): Any? {
        val softEntry = delegate[key] as SoftEntry?
        softEntry?.get()?.let { return it }
        delegate.remove(key)
        return null
    }

    override fun clear() = delegate.clear()

    private fun removeUnreachableItems() {
        var softEntry = referenceQueue.poll() as SoftEntry?
        while (softEntry != null) {
            val key = softEntry.key
            delegate.remove(key)
            softEntry = referenceQueue.poll() as SoftEntry?
        }
    }
}