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
autonomously. Every word will be an entry found in a given text
file database of all possible words, ```words.txt```.

The score for a word will be the number of letter guesses + the
number of incorrect word guesses if the correct word is guessed
without exceeding the specified maximum wrong guess limit *or*
25 if the game is lost before guessing the word correctly.

The code must include an implementation of the ```GuessingStrategy```
interface and also use that GuessingStrategy on a ```HangmanGame```
instance.

The pseudocode for a ```HangmanGame``` is:


    public int run(HangmanGame game, GuessingStrategy strategy) {
      while (game has not been won or lost) {
        ask the strategy for the next guess
        apply the next guess to the game
      }
      return game.score();
    }

A trivial strategy might be to guess 'A', then 'B', then 'C',
etc. until you've guessed every letter in the word (this will work
great for "cab"!) or you've lost.  For example, let's say the word
is _FACTUAL_.  Here is what a series of calls might look like:


    // secret word is factual, 4 wrong guesses are allowed
    HangmanGame game = new HangmanGame("factual", 4); 
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

```game.score()``` would be 5 in this case since there were 4 letter guesses
and 1 incorrect word guess made.


## Motivation

## Architecture

### Corpus


The provided corpus includes words with the following distribution of
lengths.

	+--------------------------+
	| Word Length | Occurances |
	|-------------+------------|
	|           2 |         96 |
	|           3 |        978 |
	|           4 |       3919 |
	|           5 |       8672 |
	|           6 |      15290 |
	|           7 |      23208 |
	|           8 |      28558 |
	|           9 |      25011 |
	|          10 |      20404 |
	|          11 |      15581 |
	|          12 |      11382 |
	|          13 |       7835 |
	|          14 |       5134 |
	|          15 |       3198 |
	|          16 |       1938 |
	|          17 |       1125 |
	|          18 |        594 |
	|          19 |        328 |
	|          20 |        159 |
	|          21 |         62 |
	|          22 |         29 |
	|          23 |         13 |
	|          24 |          9 |
	|          25 |          2 |
	|          27 |          2 |
	|          28 |          1 |
	+--------------------------+

### Index

There are two different types of indices used to narrow the selection
of word possibilities for the two distinct cases that may occur for
each letter guess:  either the guess is _incorrect_ and that letter
occurs _nowhere_ in that word, or the guess is _correct_ and the
letter occurs at some designated positiions.

#### Exclusionary Index

The simpler case is that in which a letter occurs nowhere within a
given word.  Such a case represents a binary condition -- either
present or not.  

	;; (word-terms-bits "")    => 0
	;; (word-terms-bits "A")   => 1
	;; (word-terms-bits "B")   => 2
	;; (word-terms-bits "C")   => 4
	;; (word-terms-bits "ABC") => 7
	;; (word-terms-bits "D")   => 8
	;; (word-terms-bits "AD")  => 9



	;; (repeatedly 3 #(with-corpus []
	;;                  (let [w (random-word *corpus*)]
	;;                   [w (word-terms-vector w)])))
	;;
	;; => (["MULTICAR"  413128610945285]
	;;     ["IDEALIZER" 311037270296857]
	;;     ["KOUMISSES" 353557414171920])

	;; (with-corpus []
	;;   (seq (map terms-vector-word
	;;          [413128610945285 311037270296857 353557414171920])))
	;;
	;;  => ("MULTICAR" "IDEALIZER" "KOUMISSES")


	;; (let [c (make-corpus)]
	;;   (pp-terms-vectors c (exclude-terms c (random-words c 5) \a)))
	;;
	;; [        WORD                        | ^@? _*ZYXWVUTSRQPONMLKJIHGFEDCBA
	;;  ----------------------------------------------------------------------
	;; [STRUGGLES                           | 00000000000111100000100001010000
	;; [BYWORDS                             | 00000001010001100100000000001010
	;; [WHICKERS                            | 00000000010001100000010110010100


	;; (let [c (make-corpus)]
	;;   (pp-terms-vectors c (exclude-terms c (random-words c 50) \a \e)))
	;;
	;; [        WORD                        | ^@? _*ZYXWVUTSRQPONMLKJIHGFEDCBA
	;;  ----------------------------------------------------------------------
	;; [SURMOUNT                            | 00000000000111100111000000000000
	;; [VOLVULUS                            | 00000000001101000100100000000000
	;; [FOUR                                | 00000000000100100100000000100000
	;; [SPOOKING                            | 00000000000001001110010101000000
	;; [BULGY                               | 00000001000100000000100001000010
	;; [IMPUDICITY                          | 00000001000110001001000100001100


	;; (with-corpus []
	;;   (words-excluding-terms (random-words *corpus* 5) \a))
	;;
	;;  => ("REPROOF")
	;;  => ("VERISMO" "QUELLED" "PREEMPTIVELY")
	;;  => ("FORETOKENED")
	;;  => ("FEMES" "CONTINGENCY")


	;; (with-corpus []
	;;   (term-frequency-distribution (random-words *corpus* 5)))
	;;
	;;   => {\A 1, \B 1, \C 4, \D 1, \E 4, \I 2, \L 3, \M 1, \N 3,
	;;       \O 2, \R 1, \S 5, \T 1, \U 2, \V 1, \W 1, \Y 2}


	;; (with-corpus []
	;;   (pp-term-distribution))
	;;
	;; | Term | Words Occurred |      % |
	;; |------+----------------+--------|
	;; |    E |         121433 |  69.98 |
	;; |    S |         104351 |  60.13 |
	;; |    I |         102392 |  59.01 |
	;; |    A |          94264 |  54.32 |
	;; |    R |          91066 |  52.48 |
	;; |    N |          84485 |  48.69 |
	;; |    T |          83631 |  48.19 |
	;; |    O |          79663 |  45.91 |
	;; |    L |          68795 |  39.64 |
	;; |    C |          55344 |  31.89 |
	;; |    D |          47219 |  27.21 |
	;; |    U |          46733 |  26.93 |
	;; |    P |          40740 |  23.48 |
	;; |    M |          39869 |  22.98 |
	;; |    G |          38262 |  22.05 |
	;; |    H |          33656 |  19.40 |
	;; |    B |          26736 |  15.41 |
	;; |    Y |          24540 |  14.14 |
	;; |    F |          17358 |  10.00 |
	;; |    V |          14844 |   8.55 |
	;; |    K |          12757 |   7.35 |
	;; |    W |          11310 |   6.52 |
	;; |    Z |           7079 |   4.08 |
	;; |    X |           4607 |   2.65 |
	;; |    Q |           2541 |   1.46 |
	;; |    J |           2467 |   1.42 |

### Strategy

### Game

## Implementation

## Status
