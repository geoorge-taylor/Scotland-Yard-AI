package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

public class MyAi implements Ai {

	public static final int depth = 3;

	// TODO: Finalize a good name for the AI
	@Nonnull @Override public String name() { return "MAG AI"; }

	/**
	 * @param board: state of the game, can use get available moves
	 * @return score: a value that represents how good the outcome of a move is
	 * desc: to get it working, just use the distance from mrX as an indicator for the best score
	 */
	private int score(Board board) {
		return 0;
	}

	/**
	 * @param board:
	 * @param depth:
	 * @return score: the best possible score from a given game state
	 * desc: if reached depth or terminal state, then return the score for that state, else recursivley keep on calling
	 * For mrX, we are trying to maximise the score, so for all possible moves, recurse for each and pick the highest score
	 * For detectives, we are trying to minimise the score.
	 * The game tree is build implicitly during the minimax calls.
	 * Only evaluate score on the leaf nodes of the state tree.
	 */
	private int minimax(Board board, int depth) {
		return 0;
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		// returns a random move, replace with your own implementation
		// here we loop through the legal moves, and call mini max on each one, and then pick the one with the highest score
		var moves = board.getAvailableMoves().asList();
		return moves.get(new Random().nextInt(moves.size()));
	}
}
