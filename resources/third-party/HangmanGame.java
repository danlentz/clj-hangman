import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class HangmanGame {
  /**
   * A enum for the current state of the game
   */
  public enum Status {GAME_WON, GAME_LOST, KEEP_GUESSING}

  /**
   * A marker for the letters in the secret words that have not been guessed yet.
   */
  public static final Character MYSTERY_LETTER = '-';

  /**
   * The word that needs to be guessed (e.g. 'FACTUAL')
   */
  private final String secretWord;

  /**
   * The maximum number of wrong letter/word guesses that are allowed (e.g. 6, and if you exceed 6 then you lose)
   */
  private final int maxWrongGuesses;

  /**
   * The letters guessed so far (unknown letters will be marked by the MYSTERY_LETTER constant). For example, 'F-CTU-L'
   */
  private final char[] guessedSoFar;

  /**
   * Set of all correct letter guesses so far (e.g. 'C', 'F', 'L', 'T', 'U')
   */
  private Set<Character> correctlyGuessedLetters = new HashSet<Character>();

  /**
   * Set of all incorrect letter guesses so far (e.g. 'R', 'S')
   */
  private Set<Character> incorrectlyGuessedLetters = new HashSet<Character>();

  /**
   * Set of all incorrect word guesses so far (e.g. 'FACTORS')
   */
  private Set<String> incorrectlyGuessedWords = new HashSet<String>();

  /**
   * @param secretWord The word that needs to be guessed
   * @param maxWrongGuesses The maximum number of incorrect word/letter guesses that are allowed
   */
  public HangmanGame(String secretWord, int maxWrongGuesses) {
    this.secretWord = secretWord.toUpperCase();
    this.guessedSoFar = new char[secretWord.length()];
    for (int i = 0; i < secretWord.length(); i++) {
      guessedSoFar[i] = MYSTERY_LETTER;
    }
    this.maxWrongGuesses = maxWrongGuesses;
  }

  /**
   * Guess the specified letter and update the game state accordingly
   * @return The string representation of the current game state
   * (which will contain MYSTERY_LETTER in place of unknown letters)
   */
  public String guessLetter(char ch) {
    assertCanKeepGuessing();
    ch = Character.toUpperCase(ch);

    // update the guessedSoFar buffer with the new character
    boolean goodGuess = false;
    for (int i = 0; i < secretWord.length(); i++) {
      if (secretWord.charAt(i) == ch) {
        guessedSoFar[i] = ch;
        goodGuess = true;
      }
    }

    // update the proper set of guessed letters
    if (goodGuess) {
      correctlyGuessedLetters.add(ch);
    } else {
      incorrectlyGuessedLetters.add(ch);
    }

    return getGuessedSoFar();
  }

  /**
   * Guess the specified word and update the game state accordingly
   * @return The string representation of the current game state
   * (which will contain MYSTERY_LETTER in place of unknown letters)
   */
  public String guessWord(String guess) {
    assertCanKeepGuessing();
    guess = guess.toUpperCase();

    if (guess.equals(secretWord)) {
      // if the guess is correct, then set guessedSoFar to the secret word
      for (int i = 0; i<secretWord.length(); i++) {
        guessedSoFar[i] = secretWord.charAt(i);
      }
    } else {
      incorrectlyGuessedWords.add(guess);
    }

    return getGuessedSoFar();
  }

  /**
   * @return The score for the current game state
   */
  public int currentScore() {
    if (gameStatus() == Status.GAME_LOST) {
      return 25;
    } else {
      return numWrongGuessesMade() + correctlyGuessedLetters.size();
    }
  }

  private void assertCanKeepGuessing() {
    if (gameStatus() != Status.KEEP_GUESSING) {
      throw new IllegalStateException("Cannot keep guessing in current game state: " + gameStatus());
    }
  }

  /**
   * @return The current game status
   */
  public Status gameStatus() {
    if (secretWord.equals(getGuessedSoFar())) {
      return Status.GAME_WON;
    } else if (numWrongGuessesMade() > maxWrongGuesses) {
      return Status.GAME_LOST;
    } else {
      return Status.KEEP_GUESSING;
    }
  }

  /**
   * @return Number of wrong guesses made so far
   */
  public int numWrongGuessesMade() {
    return incorrectlyGuessedLetters.size() + incorrectlyGuessedWords.size();
  }

  /**
   * @return Number of wrong guesses still allowed
   */
  public int numWrongGuessesRemaining() {
    return getMaxWrongGuesses() - numWrongGuessesMade();
  }

  /**
   * @return Number of total wrong guesses allowed
   */
  public int getMaxWrongGuesses() {
    return maxWrongGuesses;
  }

  /**
   * @return The string representation of the current game state
   * (which will contain MYSTERY_LETTER in place of unknown letters)
   */
  public String getGuessedSoFar() {
    return new String(guessedSoFar);
  }

  /**
   * @return Set of all correctly guessed letters so far
   */
  public Set<Character> getCorrectlyGuessedLetters() {
    return Collections.unmodifiableSet(correctlyGuessedLetters);
  }

  /**
   * @return Set of all incorrectly guessed letters so far
   */
  public Set<Character> getIncorrectlyGuessedLetters() {
    return Collections.unmodifiableSet(incorrectlyGuessedLetters);
  }

  /**
   * @return Set of all guessed letters so far
   */
  public Set<Character> getAllGuessedLetters() {
    Set<Character> guessed = new HashSet<Character>();
    guessed.addAll(correctlyGuessedLetters);
    guessed.addAll(incorrectlyGuessedLetters);
    return guessed;
  }

  /**
   * @return Set of all incorrectly guessed words so far
   */
  public Set<String> getIncorrectlyGuessedWords() {
    return Collections.unmodifiableSet(incorrectlyGuessedWords);
  }

  /**
   * @return The length of the secret word
   */
  public int getSecretWordLength() {
    return secretWord.length();
  }

  @Override
  public String toString() {
    return getGuessedSoFar() + "; score=" + currentScore() + "; status=" + gameStatus();
  }
}
