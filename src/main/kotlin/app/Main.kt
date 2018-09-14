package app

import app.WordIndex.Companion.indexDir
import kotlinx.coroutines.experimental.runBlocking
import java.util.*

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        if (!indexDir.exists()) {
            println("Hangman is not ready yet. Please build the index first.")
        } else {
            println("Hi. Please, think of a word.")
            println("What are the first and last letters ?")
            val (firstChar, lastChar) = Scanner(System.`in`).next().toCharArray()
            val firstLast = FirstLastChar(firstChar, lastChar)

            val wordIndex = WordIndex.read(indexDir, firstLast) ?: return println("I don't know words that start with '$firstChar' and end with '$lastChar'")
            runBlocking {
                val hangman = object : Hangman() {
                    override suspend fun printString(str: String) = println(str)
                    override suspend fun readString(): String = Scanner(System.`in`).next().trim()
                    override suspend fun readInt(): Int = Scanner(System.`in`).nextInt()
                }
                hangman.start(firstChar, lastChar, wordIndex)
            }
        }
    }
}