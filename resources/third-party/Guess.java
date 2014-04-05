/**
 * Common interface for GuessWord and GuessLetter
 */
public interface Guess {
  /**
   * Applies the current guess to the specified game.
   * @param game The game to make the guess on.
   */
  void makeGuess(HangmanGame game);
}
