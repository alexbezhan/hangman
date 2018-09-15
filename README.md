# Hangman

Hangman game written in Kotlin. 

It uses coroutines for better testing support. 

It uses [SCOWL words database](http://wordlist.aspell.net/) to guess the words.

It asks first and last letters and then the game begins. Hangman uses words indexed by first and last letters.

The index is already prebuilt. But you can build it again with:
```
./gradlew shadowJar
./build-index.sh
```

## What could be improved:
- Improve letter guessing algorithm: build possible subwords from words candidates and picking the best subword and then picking letter from it. Currently it picks letter without taking into account subwords.
- Use some kind of weights system for words frequency to improve the probability of correct guess on later rounds.
- Improve user interface to let the user stop the game, if the word is guessed correctly. Currently Hangman keeps asking letters if he has longer words candidates.
- Save index size by converting index key `Set<Char>` to a sorted string of characters.
