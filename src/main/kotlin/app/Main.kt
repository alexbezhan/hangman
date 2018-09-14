package app

import kotlinx.coroutines.experimental.runBlocking
import java.io.File
import java.util.*

val indexFile = File(".word-index")

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val wordIndex = WordIndex.readFrom(indexFile)
        if (wordIndex == null) {
            println("Hangman is not ready yet. Please build the index first.")
        } else {
            println("Hi. Please, think of a word.")
            println("What are the first and last letters ?")
            val (firstChar, lastChar) = Scanner(System.`in`).next().toCharArray()

            runBlocking {
                val hangman = object : Hangman(firstChar, lastChar, wordIndex) {
                    override suspend fun printString(str: String) = println(str)
                    override suspend fun readString(): String = Scanner(System.`in`).next().trim()
                    override suspend fun readInt(): Int = Scanner(System.`in`).nextInt()
                }
                hangman.start()
            }
        }
    }
}