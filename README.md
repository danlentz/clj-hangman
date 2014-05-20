# CLJ-HANGMAN

Hangman is a word-guessing game with origins that go back as far as
the Victorian era.  The word to guess is represented by a row of
dashes, giving the number of letters it contains. If the guessing
player suggests a letter or number which occurs in the word, the other
player writes it in all its correct positions. If the suggested letter
or number does not occur in the word, the other player draws one
element of the hanged man stick figure as a tally mark. The game is
over when either the guessing player exceeds a given number of
incorrect guesses, typically 5, or when all of the letters have been
guessed correctly.

## Goal


The goal is to use a provided API to play Hangman effectively and
efficiently. In particular, it is important to implement a modular
design that will accomodate "pluggable" strategies in order to support
comparitive exploration of various computer models that play the game
autonomously. 

The score for a word will be:

   # letter guesses + # number of incorrect word guesses if you
     guessed the word right before exceeding maxWrongGuesses incorrect
     guesses

or

   25 if you lost the game before guessing the word correctly.

You will need to write an implementation of the GuessingStrategy
interface and some code to use your GuessingStrategy on a HangmanGame
instance.

The pseudocode to run your strategy for a HangmanGame is:



// runs your strategy for the given game, then returns the score


public int run(HangmanGame game, GuessingStrategy strategy) {


  while (game has not been won or lost) {


    ask the strategy for the next guess


    apply the next guess to the game


  }


  return game.score();


}

A trivial strategy might be to guess 'A', then 'B', then 'C',
etc. until you've guessed every letter in the word (this will work
great for "cab"!) or you've lost.

Every word you encounter will be a word from the words.txt file.

Example

For example, let's say the word is FACTUAL.

Here is what a series of calls might look like:



HangmanGame game = new HangmanGame("factual", 4); // secret word is
factual, 4 wrong guesses are allowed


System.out.println(game);


new GuessLetter('a').makeGuess(game);


System.out.println(game);


new GuessWord("natural").makeGuess(game);


System.out.println(game);


new GuessLetter('x').makeGuess(game);


System.out.println(game);


new GuessLetter('u').makeGuess(game);


System.out.println(game);


new GuessLetter('l').makeGuess(game);


System.out.println(game);


new GuessWord("factual").makeGuess(game);


System.out.println(game);

The output would be:



-------; score=0; status=KEEP_GUESSING


-A---A-; score=1; status=KEEP_GUESSING


-A---A-; score=2; status=KEEP_GUESSING


-A---A-; score=3; status=KEEP_GUESSING


-A--UA-; score=4; status=KEEP_GUESSING


-A--UAL; score=5; status=KEEP_GUESSING


FACTUAL; score=5; status=GAME_WON

game.score() will be 5 in this case since there were 4 letter guesses
and 1 incorrect word guess made.

Sample Data

As a baseline, here are scores for a reasonably good guessing strategy
against a set of 15 random words. Your strategy will likely be better
for some of the words and worse for other words, but the average
score/word should be in the same ballpark.

COMAKER = 25 (was not able to guess the word before making more than 5 mistakes)
CUMULATE = 9
ERUPTIVE = 5
FACTUAL = 9
MONADISM = 8
MUS = 25 (was not able to guess the word before making more than 5 mistakes)
NAGGING = 7
OSES = 5
REMEMBERED = 5
SPODUMENES = 4
STEREOISOMERS = 2
TOXICS = 11
TRICHROMATS = 5
TRIOSE = 5
UNIFORMED = 5



## Motivation

## Architecture

### Corpus

### Index

### Strategy

### Game

## Implementation

## Status
