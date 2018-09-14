package app

import java.io.*

class WordIndex(val index: Map<Set<Char>, List<String>>, val allWords: List<String>) : Serializable {
    operator fun get(key: Set<Char>): List<String>? =
            if (key.isEmpty()) allWords
            else index[key]

    fun writeTo(file: File) {
        ByteArrayOutputStream().use { bytesOutputStream ->
            val objectOutputStream = ObjectOutputStream(bytesOutputStream)
            objectOutputStream.writeObject(this)
            objectOutputStream.flush()
            val bytes = bytesOutputStream.toByteArray()
            file.writeBytes(bytes)
        }

    }

    companion object {
        private val serialVersionUID: Long = 1

        fun readFrom(file: File): WordIndex? =
                if (!file.exists()) {
                    null
                } else {
                    val bis = ByteArrayInputStream(file.readBytes())
                    ObjectInputStream(bis).use { oin ->
                        oin.readObject() as WordIndex
                    }
                }
    }
}