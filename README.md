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

In the abstract, this problem is to create a framework for playing
hangman with support for employment of various "pluggable" strategies
which an end-user may implement.  Rather than approach this project
with the objective of achieving the most optimized performance for one
given strategy, we approach with a slightly different motiovation:
**support the most expressive, extensible, and generalized ability to
define arbitrarily complex strategies, while remaining relatively
efficient**.

## Architecture

This solution is based on the notion of a _tuple store_ with a
_generalized query_ interface called a _Graph_.  The information in a
Graph is encoded as a series of assertions of relations among its
constituents. They are encoded as tuples:

    [subject predicate object]

There are many advantages to this approach, such as the ability to
persist and restore graphs, and the inherent parallelism this type of
computation can achieve using Clojures _reducers_ library with
fork/join multiprocessing. The most significant advantage, thopugh, is
that it provides the ability to represent and efficiently query
arbitrarily complex and dynamically extensible concepts of data.  We
may easily encode and index in unique ways by making assertions like
_"EVERYTHING" is 10 letters long_ or _"SOME" begins with 'S'_:

    ["EVERYTHING" :length 10]
    ["SOME"       \S       0]

But we may also arbitrarily extend our graph.  Say, for example, in
the future we want to extend our game to provide a clue to the word
being guessed that represented its possible "part of speech" (e.g.,
```:NOUN :VERB :ADJECTIVE :SLANG``` and so on).  We would simply extend our
tuple-store with the new assertions:

    ["CAT" :isa :NOUN]
    ["CAT" :isa :SLANG]

The interface and technique for adding a strategy that incorporates
this new type of information into its calculations would remain
exactly the same.  It would just then be able to perform additional
queries against the Graph to select for a given part of speech, or
dertermine the part of speech for a given word.  Because the data of a
Graph, tuples, are so generally defined, there can be clean separation
of concerns between implementation of strategy and mechanics of the
underlying indexing.  In fact, for inspiration about how various
shemata can be used to encode new kinds of concepts within a given
Graph, one might look to RDFS or OWL systems of description logic,
frequently used for machine learning and knowledge representation. 

Finally, the relations of a graph may be reified to express
meta-relations among the tuples themselves.  For example, one could
invent a new _predicate_, ```:level-of-confidence``` to express the
ordinal precidence between subsequently numbered definitions of a
word in some dictionary:

    ["CAT" :def "A fuzzy creature..."    ]
    ["CAT" :def "Person; 'A cool cat'..."]
    [["CAT" :def "A fuzzy creature..."    ] :confidence 1]
    [["CAT" :def "Person; 'A cool cat'..."] :confidence 2]

Its also possible to fully embrace the meta and to include assertions
about the Graph itself or about other Graphs.

### Tuple-Store

Tuples are stored in Graphs which consist of a set of such tuples
combined with appropriate indexing to suport generalized query in the
form of another tuple that may use the special value _nil_ to
represent "wildcard" or some literal to select for that value.
Therefore, to enumerate all triples in a given Graph, g:

    (query g nil nil nil)

To find all words of length 3:

    (query g nil :length 3)


By composing multiple queries and performing aggregate operations to
create new graphs, we express arbitrarily complex data queries using a
well defined algebra of set operations. 

#### Indexing

A graph, under the hood, is indexed in some fashion so as to be
queried efficiently by corresponding specializations of the
```query``` multifunction.  This allows for alternative indexing
techniques to be introduced in the future without change to api.
There is a default, fully indexed graph implementation provided. It
is a _hierarchical index_ extending over various permutations of
```[s p o]``` -- _subject, predicate, object_. Please keep in mind
that the internal structre of a Graph's index should be considered an
implementation detail and not relied upon.  With that in mind, an
```[s p o]``` index might appear as follows:

    {[s p o] {
              "EVERYTHING" {
                            \E {
                                 0  ["EVERYTHING" \E 0]
                                 2  ["EVERYTHING" \E 2] }
                            \V {
                                 1  ["EVERYTHING" \V 1] }

                            ...}
                            
              "EVERYTIME"  {
                            \E {
                                 0  ["EVERYTIME"  \E 0]
                                 2  ["EVERYTIME"  \E 2] 
                                 8  ["EVERYTIME"  \E 8] }
                            ...}
              ...}
      ...}


You'll notice that when one fully descends the index, the last value
when one traverses the constituents of a given tuple is the tuple
itself.  There are a couple of thoughts behind this structure, the
first being the efficiency of holding the triple as a result of
successful query, rather than recreating it.  Additionally, this
allows for a symmetry of structure as nested key/value indices without
imposing that the deepest layer is instead a set.  Thus, the actual
schema of an ```[s p o]``` index is _subject, predicate, object, identity_:

    (s p o . i)

Likewise, an ```[p o s]``` index might look like:

    {[p o s] {
              \E {
                   0 {           
                       "EVERYTHING" ["EVERYTHING" \E 0]
                       "EVERYTIME"  ["EVERYTIME"  \E 0]
                       "EVERYONE"   ["EVERYONE"   \E 0]
                       "EVERYPLACE" ["EVERYPLACE" \E 0] 
                       "EVERYMAN"   ["EVERYMAN"   \E 0]
                       ...}
                   1 {
                       "TEAM"       ["TEAM"       \E 1]
                       "LEADER"     ["LEADER"     \E 1]
                       ...}
                   ...}
             ...}
      ...}

#### Context

Context refers to a default global state as may be in effect at some
point in program execution. It is a graph among a global collection
represented and indexed by UUID identifier.  Once a graph has been
interned in this index, the fully built graph structure will be stored
and associated with both its UUID identifier and the set of triples it
contains.  The operator ```intern-graph``` is used to instate a given
graph context, after which that graph will remain indexed accessible
by content.  Notice that after a given graph has been interned by its
use as a context, any query returning a graph with the same contents
returns the same, physical graph.  It is only indexed once.  Think of
this as a kind of _graph memoization_.  Or, in other words, graphs are
addressable by content.

A higher-level query protocol, ```select```, is used to perform
context-aware query.  ```select``` indirectly invokes the appropriate
query multifunction according to the class of the input graph and the
supplied constituents of a query triple.

	;;;
	;;; Identity and Context: examples.
	;;;

	;; (select (graph #{[1 2 3] [4 5 6]}) [nil nil nil])
	;;   => #<Graph e9a62310-7238-1195-8101-7831c1bbb832 (2 triples)>

	;; (with-context #{[1 2 3]}
	;;   (query (graph *context*) 1 2 nil))
	;;   => #{[1 2 3]}

	;; (with-context (select (graph #{[1 2 3] [4 5 6]}) [nil nil nil])
	;;   (triples (select (graph nil) [nil nil nil])))
	;;   => #{[4 5 6] [1 2 3]}

	;; (select (graph #{[1 2 3] [4 5 6]}) [nil nil nil])
	;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>
	;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>
	;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>

	;; (with-context (select (graph #{[1 2 3] [4 5 6]}) [nil nil nil])
	;;   (select (graph nil) [nil nil nil]))
	;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>
	;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>
	;;   => #<Graph 0322eb40-723c-1195-8101-7831c1bbb832 (2 triples)>



### Words


#### Letter Occurances

Occurances of a letter in a given word are represented by a bitmap
indicating each position in which that letter is found. This allows
all occurance positions to be encoded with a single, 64-bit integer,
or, zero if the letter does not occur at all.  So, occurances of 'E'
in "EVERYWHERE" would be represented as follows:

      EVERYWHERE <- word
      ..........
      1010000101 <- actual 
      ----------
    = 645

Now, the above example represents a special case because of the
symmetry of the occurances of 'E'.  Because English words are read
left-to-right, the most intuitive representation of 'position' should
also count from left to right.  Therefore, the bits in the
word-letter-position-mask are actually in reverse. This type of
'endian-ness' should be familiar to those who have worked with other
types of binary encoding:

      SOMEWHERE  <- word
      .........
      000100101  <- reversed bits of position mask
      101001000  <- actual 
      ---------
    = 328

Finally, the actual triples representing the occurances of 'E' in
words "SOMEWHERE" and "EVERYWHERE":

    ["EVERYWHERE" \E 645]
    ["SOMEWHERE"  \E 328]

Why do we use this bitmap technique? There are two reasons why letter
position is encoded this way, rather than just adding multiple triples
(one for each occurance position) as shown in the simplified example
above. First, it is beneficial for efficiency to be able to select all
words with a given letter/position using only one query.  But, more
importantly, this approach gives us the ability to implicitly exclude
words where a given letter occurs in other positions _in addition_ to
the ones specified.  That is, if we have the following game in
progress:

    _Y_Y__

We are interested in possible solutions that contain 'Y' _only_ in the
given positions since, by the rules of the game, all positions of a
given letter will be revealed when that letter is guessed.  Therefore,
we would like to exclude from consideration choices such as
```SYZYGY``` which contain 'Y' at positions in addition to those
displayed.  This is easily accomplished using the bitmap
representation:

    (word-letter-position-mask "_Y_Y__" \Y)
      
      => 10

    (word-letter-position-mask "SYZYGY" \Y)

      => 42

Remembering that ```nil``` represents the wildcard, then using this
approach, we may query very efficiently for all words with Y _only_ in
the given positions.  We would simply query for all triples that
match the following:

    [nil  \Y  10]


#### Hangman Queries

Strategies for the game of hangman can be coded using the coposition
of the following types of queries: _word length_, _letters not
present_, and _letter at position(s)_.  These are the types of
information revealed to us during the course of a game.

##### Length


    (words-of-length @word-db 2)

     => #{"PE" "EN" "UH" "SI" "IT" "PI" "FA" "MY" "AM" "BI" "YO" "NA" "OH"
          "MU" "LI" "NU" "AY" "AH" "IF" "HO" "AX" "OD" "NE" "ON" "OW" "EX"
          "BO" "JO" "KA" "IS" "TA" "EH" "AT" "EL" "XU" "OY" "UP" "MM" "YE"
          "MI" "UM" "PA" "UT" "GO" "BY" "XI" "MO" "AR" "AW" "TI" "ID" "BA"
          "SH" "MA" "OE" "AD" "WO" "OM" "HE" "SO" "DO" "AL" "LA" "DE" "AS"
          "NO" "ET" "AG" "BE" "OX" "OR" "EM" "ED" "WE" "US" "HA" "AB" "YA"
          "RE" "IN" "ES" "OS" "UN" "LO" "HI" "ER" "AE" "HM" "AI" "OP" "OF"
          "AN" "TO" "AA" "EF" "ME"}


##### Exclusionary

    (words-excluding @word-db \A \E \I \O \U)
    
    => #{"SCRY" "CRYPTS" "WHYS" "TSKTSKS" "NTH" "MYTH" "MY" "SYNC" "CWMS" 
         "CYST" "XYSTS" "STY" "DRYS" "HMM" "HYPS" "GYP" "FLYBY" "FRY" "PSYCHS"
         "SLYLY" "FLYBYS" "TRY" "HYMN" "SHH" "DRYLY" "GYPSY" "WHY" "WRYLY"
         "XYST" "CRWTHS" "SYPHS" "SYNCH" "WYNDS" "RYND" "TSKTSK" "SHY" "MM"
         "GLYCYL" "PHPHT" "STYMY" "WYCH" "SYLPHS" "SHYLY" "TRYST" "DRY" "CRY"
         "SPRY" "MYRRH" "THY" "BY" "FLY" "MYTHY" "LYNX" "BYRLS" "CRYPT" "GHYLL"
         "NYMPH" "GLYPH" "PHT" "SKY" "GYPS" "SYPH" "SYNTHS" "PYX" "THYMY"
         "SH" "CWM" "GYM" "LYMPH" "LYMPHS" "GYMS" "TRYSTS" "BYRL" "SYNCHS"
         "TYPPS" "PFFT" "WRY" "PYGMY" "WYN" "SYLPHY" "TYPP" "TSK" "SPRYLY"
         "SYNTH" "RHYTHM" "SYN" "GLYPHS" "HYMNS" "NYMPHS" "TSKS" "TYPY" "WYNS"
         "MYTHS" "BRR" "RHYTHMS" "BYS" "PSYCH" "MYRRHS" "SYNCS" "PRY" "WYNNS"
         "CRWTH" "PLY" "WYNN" "BRRR" "RYNDS" "HM" "SLY" "GLYCYLS" "HYP" "GHYLLS"
         "PSST" "SYZYGY" "XYLYLS" "SYLPH" "FLYSCH" "WYND" "SPY" "LYNCH" "CYSTS"
         "XYLYL"}

So, composing _length_ and _exclusion_ we can easily narrow the set of
possible solutions in a hypothetical game in which a two letter word
contains no vowels:


    (clojure.set/intersection
      (words-of-length @word-db 2)
      (words-excluding @word-db \A \E \I \O \U \Y))

     => #{"MM" "SH" "HM"}


##### Positional


    (words-with-letter-positions @word-db \E 1 3 5 7)
    
    => #{"TELEMETER" "SEVERENESS" "TELEMETERING" "BEJEWELED" "TELEMETERS"
         "TEREBENES" "TEREBENE" "SERENENESS"}



    (words-with-letter-positions @word-db \A 0 1)
    
    => #{"AAS" "AAHS" "AAL" "AARDWOLVES" "AAHED" "AALS" "AARDWOLF" "AAH"
         "AASVOGELS" "AALII" "AAHING" "AA" "AALIIS" "AARRGHH" "AASVOGEL"
         "AARGH" "AARRGH"}


##### Letter Frequency Distribution

The provided corpus includes words with the following distribution of
lengths.


    +------+----------------+--------+
	| Term | Words Occurred |      % |
	|------+----------------+--------|
	|    E |         121433 |  69.98 |
	|    S |         104351 |  60.13 |
	|    I |         102392 |  59.01 |
	|    A |          94264 |  54.32 |
	|    R |          91066 |  52.48 |
	|    N |          84485 |  48.69 |
	|    T |          83631 |  48.19 |
	|    O |          79663 |  45.91 |
	|    L |          68795 |  39.64 |
	|    C |          55344 |  31.89 |
	|    D |          47219 |  27.21 |
	|    U |          46733 |  26.93 |
	|    P |          40740 |  23.48 |
	|    M |          39869 |  22.98 |
	|    G |          38262 |  22.05 |
	|    H |          33656 |  19.40 |
	|    B |          26736 |  15.41 |
	|    Y |          24540 |  14.14 |
	|    F |          17358 |  10.00 |
	|    V |          14844 |   8.55 |
	|    K |          12757 |   7.35 |
	|    W |          11310 |   6.52 |
	|    Z |           7079 |   4.08 |
	|    X |           4607 |   2.65 |
	|    Q |           2541 |   1.46 |
	|    J |           2467 |   1.42 |
    +--------------------------------+




### Index

There are two different types of indices used to narrow the selection
of word possibilities for the two distinct cases that may occur for
each letter guess:  either the guess is _incorrect_ and that letter
occurs _nowhere_ in that word, or the guess is _correct_ and the
letter occurs at some designated positions.

#### Exclusionary Index

The simpler case is that in which a letter occurs nowhere within a
given word.  Such a case represents a binary condition -- either
present or not.  Thus, using a 32-bit integer, we may represent the
presence (or absence) of 32 individual search "terms".  Please do not
be confused by the use of "term" here -- in the context this project,
a "search term" is a single letter.  The terminology is just used to
maintain consistency with other literature on inverted indices in
which a "search term" is more commonly a word.

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
