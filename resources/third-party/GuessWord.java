/**
 * A Guess that represents guessing a word for the current Hangman game
 */
public class GuessWord implements Guess {
  private final String guess;

  public GuessWord(String guess) {
    this.guess = guess;
  }

  @Override
  public void makeGuess(HangmanGame game) {
    game.guessWord(guess);
  }

  @Override
  public String toString() {
    return "GuessWord[" + guess + "]";
  }
}
