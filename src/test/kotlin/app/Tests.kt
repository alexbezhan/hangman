package app

import org.junit.Test

class Tests {
    @Test
    fun test() {
        val knownWords = listOf("among", "favor", "labor", "rumor")
        val knownLetters = listOf(null, 'a', null, null, null)
        println(knownLetters.withIndex().forAll { (i, l) -> "among"[i] == l })
//        println(knownWords.filter { word -> knownLetters.withIndex().forAll { (i, l) -> word[i] == l } })
    }
}