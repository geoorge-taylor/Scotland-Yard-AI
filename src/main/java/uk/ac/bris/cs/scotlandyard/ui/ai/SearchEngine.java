package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
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
            if (!StateUtils.isMrXTurn(node.getState())
                    && StateUtils.hasMrXRevealedLocation(node.getState())) {
                return evaluator.evaluateNodeSpread(node);
            } else {
                return evaluator.evaluateNodeCatch(node);
            }
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

        if (!StateUtils.hasMrXRevealedLocation(node.getState())) {
            Set<Integer> detectiveLocations = StateUtils.getDetectiveLocations(node.getState());
            moves.sort((a, b) -> {
                int scoreA = evaluator.spreadHeuristicDetectives(StateUtils.getMoveSource(a), StateUtils.getMoveDestination(a), detectiveLocations);
                int scoreB = evaluator.spreadHeuristicDetectives(StateUtils.getMoveSource(b), StateUtils.getMoveDestination(b), detectiveLocations);
                return Integer.compare(scoreB, scoreA); // higher spread first
            });
        } else {
            moves.sort((a, b) -> {
                int distA = evaluator.catchHeuristicDetectives(StateUtils.getMoveDestination(a), node.getMrXLocation());
                int distB = evaluator.catchHeuristicDetectives(StateUtils.getMoveDestination(b), node.getMrXLocation());
                return Integer.compare(distB, distA); // ascending order
            });
        }

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

    public Move pickBestMrXMove(ImmutableSet<Move> availableMoves, Move defaultMove, Node rootNode, Pair<Long, TimeUnit> timeoutPair) {
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        int bestScore = Integer.MIN_VALUE;
        Move bestMove = defaultMove;

        for (Move move : availableMoves) {
            Node child = new Node(rootNode.getState().advance(move));
            child.setMrXLocation(StateUtils.getMoveDestination(move));
            int score = minimax(child, TREE_DEPTH - 1, alpha, beta);

            if (score > bestScore) {
                bestMove = move;
                bestScore = score;
            }

            alpha = Math.max(alpha, bestScore);
        }

        return bestMove;
    }

    public Move pickBestDetectiveMove(ImmutableSet<Move> availableMoves, Move defaultMove, Node rootNode, Pair<Long, TimeUnit> timeoutPair) {
        // When mrX location is not revealed yet, spread detectives out as much as possible, else head towards mrX last known location
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        int bestScore = Integer.MAX_VALUE;
        Move bestMove = defaultMove;

        for (Move move : availableMoves) {
            System.out.println("One possible move for the detectives is: ");
            Node child = new Node(rootNode.getState().advance(move));
            child.setMrXLocation(rootNode.getMrXLocation());
            int score = minimax(child, TREE_DEPTH - 1, alpha, beta);

            if (score < bestScore) {
                bestScore = score;
                bestMove = move;
            }

            beta = Math.min(beta, bestScore);
        }

        return bestMove;
    }
}
