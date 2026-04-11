package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import jakarta.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MyAi implements Ai {
	@Nonnull @Override public String name() { return "MAGIC AI"; }

	@Nonnull @Override public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {

		var moves = board.getAvailableMoves().asList();
		Move bestMove = moves.get(new Random().nextInt(moves.size()));

		// extract the players for the game state
		Player mrX = StateUtils.extractMrXPlayer(board);
		var detectives = StateUtils.extractDetectivePlayers(board);
		Board.GameState state = new MyGameStateFactory().build(board.getSetup(), mrX, detectives);
		Map<Integer, Integer> distanceMap = StateUtils.multiSourceBFS(board);
		// Make a map of how far each detective is away from point of mrX

		Node root = new Node(state);
		root.setMrXLocation(mrX.location());

		StateEvaluator evaluator = new StateEvaluator();
		SearchEngine search = new SearchEngine(evaluator, distanceMap);

		return StateUtils.isMrXTurn(board)
				? search.pickBestMrXMove(bestMove, root, timeoutPair)
				: board.getAvailableMoves().asList().get(0);
	}
}
