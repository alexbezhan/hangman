package app

import java.io.*

class WordIndex(val index: Map<Set<Char>, List<String>>) : Serializable {
    operator fun get(key: Set<Char>): List<String>? = index[key]

    fun writeTo(dir: File, firstLast: FirstLastChar) {
        ByteArrayOutputStream().use { bytesOutputStream ->
            val objectOutputStream = ObjectOutputStream(bytesOutputStream)
            objectOutputStream.writeObject(this)
            objectOutputStream.flush()
            val bytes = bytesOutputStream.toByteArray()
            indexFile(dir, firstLast).writeBytes(bytes)
        }

    }

    fun merge(other: WordIndex): WordIndex =
            WordIndex(index.mergeReduce(other.index) { wordsA, wordsB ->
                wordsA + wordsB
            })

    companion object {
        private val serialVersionUID: Long = 1

        fun indexFile(dir: File, firstLast: FirstLastChar) = File(dir, "${firstLast.firstChar}${firstLast.lastChar}")

        fun read(dir: File, firstLast: FirstLastChar): WordIndex? {
            if (!dir.exists()) return null

            val indexFile = indexFile(dir, firstLast)
            if (!indexFile.exists()) return null

            val fileBytes = indexFile.readBytes()
            if (fileBytes.isEmpty()) return null

            val bis = ByteArrayInputStream(fileBytes)
            return ObjectInputStream(bis).use { oin ->
                oin.readObject() as WordIndex
            }
        }
    }
}

fun <K, V> Map<K, V>.mergeReduce(other: Map<K, V>, reduce: (V, V) -> V = { a, b -> b }): Map<K, V> {
    val result = LinkedHashMap<K, V>(this.size + other.size)
    result.putAll(this)
    other.forEach { e -> result[e.key] = result[e.key]?.let { reduce(e.value, it) } ?: e.value }
    return result
}