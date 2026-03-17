package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import jakarta.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MyAi implements Ai {
	// Only look 3 rounds into the future
	public static final int treeDepthLimit = 3;
	public static final int BFSDepthLimit = 10;

	@Nonnull @Override public String name() { return "MAG AI"; }

	// Had to type cast for some reason ?
	private ImmutableSet<Piece.Detective> getDetectives(Board board) {
		return board.getPlayers().stream()
				.filter(p -> !p.isMrX())
				.map(p -> (Piece.Detective) p)
				.collect(ImmutableSet.toImmutableSet());
	}

	private Piece getMrX(Board board) {
		return board.getPlayers().stream()
				.filter(Piece::isMrX).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No MrX"));
	}

	// TODO: Make this record all detectives with just one method call -- so inefficient !!
	private int getShortestDistanceToDetective(
			ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph,
			int detectiveLocation, int mrXLocation) {

		if (detectiveLocation == mrXLocation) return 0;

		Queue<Integer> queue = new ArrayDeque<>();
		Set<Integer> visited = new HashSet<>();
		Map<Integer, Integer> distances = new HashMap<>();

		queue.add(mrXLocation);
		visited.add(mrXLocation);
		distances.put(mrXLocation, 0);

		while (!queue.isEmpty()) {
			int current = queue.poll();
			int currentDist = distances.get(current);
			if (currentDist >= BFSDepthLimit) continue;

			for (Integer neighbor : graph.adjacentNodes(current)) {
				if (!visited.contains(neighbor)) {
					visited.add(neighbor);
					distances.put(neighbor, currentDist + 1);
					queue.add(neighbor);

					if (neighbor == detectiveLocation) {
						return currentDist + 1; // found detective
					}
				}
			}
		}

		return Integer.MAX_VALUE; // no way to get to detective
	}

	private Board.GameState applyDetectiveResponse(Board.GameState board, ImmutableSet<Move> moves) {
		Board.GameState currentBoard = board;
		for (Move move : moves) currentBoard = board.advance(move);
		return currentBoard;
	}

	// TODO: Implement this!
	// given the board, return all the possible combinations of detective moves so we can represent nodes of the tree as one whole detective turn
	private ImmutableSet<ImmutableSet<Move>> getAllDetectiveResponses(Board board) {
		return ImmutableSet.of();
	}

	/**
	 * @param board: state of the game, can use get available moves
	 * @return score: a value that represents how good the outcome of a move is
	 * desc: to get it working, just use the distance from mrX to any detective as an indicator for the best score
	 */
	private int score(Board board) {
		ImmutableSet<Piece.Detective> detectives = getDetectives(board);
		Piece mrX = getMrX(board);
		GameSetup setup = board.getSetup();
		int bestDistance = 0;

		for (Piece.Detective detective : detectives) {
			int location = board.getDetectiveLocation(detective).get();
			int distance = getShortestDistanceToDetective(setup.graph, location, 0);  // how to pass through mrX location ???
			bestDistance = Math.min(distance, bestDistance);
		}

		return bestDistance;
	}

	/**
	 * @param board:
	 * @return score: the best possible score from a given game state
	 * For mrX, we are trying to maximise the score, so for all possible moves, recurse for each and pick the highest score
	 * For detectives, we are trying to minimise the score.
	 * The game tree is build implicitly during the minimax calls.
	 */
	private int minimax(Board.GameState board, int depth, boolean isMrXTurn) {
		ImmutableSet<Move> legalMoves = board.getAvailableMoves();
		if (depth == 0 || !board.getWinner().isEmpty() || legalMoves.isEmpty()) {
			return score(board);  // calculate score at terminal states
		}

		if (isMrXTurn) {
			int bestScore = Integer.MIN_VALUE;
			for (Move move : legalMoves) {
				Board.GameState nextState = board.advance(move);
				int score = minimax(nextState, depth - 1, false);
				bestScore = Math.max(bestScore, score);
			}
			return bestScore;
		} else {
			ImmutableSet<ImmutableSet<Move>> detectiveResponses = getAllDetectiveResponses(board);
			int bestScore = Integer.MAX_VALUE;
			for (ImmutableSet<Move> response : detectiveResponses) {
				Board.GameState nextState = applyDetectiveResponse(board, response);
				int score = minimax(nextState, depth - 1, true);
				bestScore = Math.min(bestScore, score);
			}
			return bestScore;
		}
	}

	// here we loop through the legal moves, and call mini max on each one, and then pick the one with the highest score
	// pickMove is also executed on detective turn so need to separate the logic for this too.
	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		var moves = board.getAvailableMoves().asList();
		Move bestMove = moves.get(new Random().nextInt(moves.size())); // default is a random move
		int bestScore = Integer.MIN_VALUE;

		for (Move move : moves) {
			var nextState = board.advance(move); // how the fuck do i call advance ??
			int score = minimax(nextState, treeDepthLimit - 1, false); // mrx turn next
			if (score > bestScore) {
				bestMove = move;
				bestScore = score;
			}
		}

		return bestMove;
	}
}
