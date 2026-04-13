package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SearchEngine {
    private static final int TREE_DEPTH = 5;
    private static final int MOVE_LIMIT = 10;
    private final StateEvaluator evaluator;

    public SearchEngine(StateEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    private int minimax(Node node, int depth, int alpha, int beta) {
        if (depth == 0 || node.isTerminal()) {
            return evaluator.evaluateNode(node);
        }

        return StateUtils.isMrXTurn(node.getState())
                ? maximiseMrX(node, depth, alpha, beta)
                : minimiseDetectives(node, depth, alpha, beta);
    }

    private List<Move> sortTopMovesMrX(Node node) {
        List<Move> moves = new ArrayList<>(node.getState().getAvailableMoves());
        Set<Integer> detectiveLocations = StateUtils.getDetectiveLocations(node.getState());

        moves.sort((a, b) -> {
            int distA = evaluator.heuristicMrX(StateUtils.getMoveDestination(a), detectiveLocations);
            int distB = evaluator.heuristicMrX(StateUtils.getMoveDestination(b), detectiveLocations);
            return Integer.compare(distA, distB); // descending order
        });

        return moves.stream().limit(MOVE_LIMIT).toList();
    }

    private List<Move> sortTopMovesDetectives(Node node) {
        List<Move> moves = new ArrayList<>(node.getState().getAvailableMoves());

        moves.sort((a, b) -> {
            int distA = evaluator.heuristicDetectives(StateUtils.getMoveDestination(a), node.getMrXLocation());
            int distB = evaluator.heuristicDetectives(StateUtils.getMoveDestination(b), node.getMrXLocation());
            return Integer.compare(distB, distA); // ascending order
        });

        return moves.stream().limit(MOVE_LIMIT).toList();
    }

    private int maximiseMrX(Node node, int depth, int alpha, int beta) {
        int maxEval = Integer.MIN_VALUE;

        for (Move move : sortTopMovesMrX(node)) {
            Board.GameState childState = node.getState().advance(move);
            Node child = new Node(childState);
            child.setPriorMrXMove(move);
            child.setMrXLocation(StateUtils.getMoveDestination(move));

            int eval = minimax(child, depth - 1, alpha, beta);
            maxEval = Math.max(maxEval, eval);
            alpha = Math.max(alpha, eval);
            if (beta <= alpha) break;
        }

        return maxEval;
    }

    private int minimiseDetectives(Node node, int depth, int alpha, int beta) {
        int minEval = Integer.MAX_VALUE;

        for (Move move : sortTopMovesDetectives(node)) {
            Board.GameState childState = node.getState().advance(move);
            Node child = new Node(childState);
            child.setPriorDetectiveMove(move);
            child.setMrXLocation(node.getMrXLocation());

            int eval = minimax(child, depth - 1, alpha, beta);
            minEval = Math.min(minEval, eval);
            beta = Math.min(beta, eval);
            if (beta <= alpha) break;
        }

        return minEval;
    }

    public Move pickBestMrXMove(Move defaultMove, Node rootNode, Pair<Long, TimeUnit> timeoutPair) {
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        int bestScore = Integer.MIN_VALUE;
        Move bestMove = defaultMove;

        for (Move move : rootNode.getState().getAvailableMoves()) {
            Node child = new Node(rootNode.getState().advance(move));
            child.setPriorMrXMove(move);
            child.setMrXLocation(StateUtils.getMoveDestination(move));

            int score = minimax(child, TREE_DEPTH - 1, alpha, beta);
            if (score > bestScore) {
                bestMove = child.getPriorMrXMove();
                bestScore = score;
            }

            alpha = Math.max(alpha, bestScore);
        }

        return bestMove;
    }
}
