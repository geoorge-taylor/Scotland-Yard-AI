package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import io.atlassian.fugue.Pair;
import jakarta.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MyAi implements Ai {
	@Nonnull @Override public String name() { return "MAGIC AI"; }
	List<Move> previousMoves = new ArrayList<>();
	Player initialMrX;
	ImmutableList<Player> initialDetectives;

	@Nonnull @Override public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {

		var moves = board.getAvailableMoves().asList();
		Move bestMove = moves.get(new Random().nextInt(moves.size()));

		// This is the start of the whole game, record the initial players
		if (previousMoves.isEmpty()) {
			initialMrX = StateUtils.extractMrXPlayer(board);
			initialDetectives = StateUtils.extractDetectivePlayers(board);
		}

		// apply the previous moves to get to the 'current' game state
		Board.GameState state = new MyGameStateFactory().build(board.getSetup(), initialMrX, initialDetectives);

		for (Move previousMove : previousMoves) {
			System.out.println("Apply previous move: " + previousMove);
			state = state.advance(previousMove);
		}

		// Pre compute all BFS distances in the game
		var distanceMap = StateUtils.buildDistanceMap(board);

		// get the location of mrX -- either actual location or last known one
		int mrXLocation = StateUtils.isMrXTurn(state)
				? StateUtils.getActualMrXLocation(state)
				: StateUtils.getLastKnownMrXLocation(state);

		Node root = new Node(state);
		root.setMrXLocation(mrXLocation);

		StateEvaluator evaluator = new StateEvaluator(distanceMap);
		SearchEngine search = new SearchEngine(evaluator);

		Move pickedMove = StateUtils.isMrXTurn(board)
				? search.pickBestMrXMove(board.getAvailableMoves(), bestMove, root, timeoutPair)
				: search.pickBestDetectiveMove(board.getAvailableMoves(), bestMove, root, timeoutPair);

		previousMoves.add(pickedMove);
		return pickedMove;
	}
}
