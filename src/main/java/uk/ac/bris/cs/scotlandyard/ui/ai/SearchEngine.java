package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class SearchEngine {
    private static final int TREE_DEPTH = 5;
    private static final int MOVE_LIMIT = 20;
    public int nodeCount = 0;
    private final StateEvaluator evaluator;
    private final Map<StateKey, TableEntry> transpositionTable = new HashMap<>();
    private final Map<Integer, Move> killerMovesMrX = new HashMap<>();
    private final Map<Integer, Move> killerMovesDetectives = new HashMap<>();

    public SearchEngine(StateEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    // Helper to build the key from a Node
    private StateKey createKey(Node node) {
        Map<Piece, Integer> detLocs = new HashMap<>();
        for (Piece.Detective d : StateUtils.extractDetectivePieces(node.getState())) {
            detLocs.put(d, node.getState().getDetectiveLocation(d).orElse(0));
        }
        return new StateKey(
                node.getMrXLocation(),
                detLocs,
                StateUtils.isMrXTurn(node.getState()),
                node.getState().getMrXTravelLog().size()
        );
    }

    private int minimax(Node node, int depth, int alpha, int beta) {
        StateKey key = createKey(node);

        if (transpositionTable.containsKey(key)) {
            TableEntry entry = transpositionTable.get(key);
            if (entry.depth() >= depth) {
                return entry.score();
            }
        }

        if (depth == 0 || node.isTerminal()) {
            if (!StateUtils.isMrXTurn(node.getState())
                    && StateUtils.hasMrXRevealedLocation(node.getState())) {
                return evaluator.evaluateNodeSpread(node);
            } else {
                return evaluator.evaluateNodeCatch(node);
            }
        }

        // 3. Store the result in the table before returning
        int resultScore = StateUtils.isMrXTurn(node.getState())
                ? maximiseMrX(node, depth, alpha, beta)
                : minimiseDetectives(node, depth, alpha, beta);

        transpositionTable.put(key, new TableEntry(resultScore, depth));
        return resultScore;
    }

    private List<Move> sortTopMovesMrX(Node node, int depth) {
        List<Move> moves = new ArrayList<>(node.getState().getAvailableMoves());
        Set<Integer> detectiveLocations = StateUtils.getDetectiveLocations(node.getState());

        moves.sort((a, b) -> {
            int distA = evaluator.catchHeuristicMrX(StateUtils.getMoveDestination(a), detectiveLocations);
            int distB = evaluator.catchHeuristicMrX(StateUtils.getMoveDestination(b), detectiveLocations);
            return Integer.compare(distB, distA); // descending order // apparently it is distB, distA not distA, distB?
        });

        Move killer = killerMovesMrX.get(depth);
        if (killer != null && moves.contains(killer)) {
            moves.remove(killer);
            moves.add(0, killer);
        }

        return moves.stream().limit(MOVE_LIMIT).toList();
    }

    private List<Move> sortTopMovesDetectives(Node node, int depth) {
        List<Move> moves = new ArrayList<>(node.getState().getAvailableMoves());

        if (!StateUtils.hasMrXRevealedLocation(node.getState())) {
            Set<Integer> detectiveLocations = StateUtils.getDetectiveLocations(node.getState());
            moves.sort((a, b) -> {
                int scoreA = evaluator.spreadHeuristicDetectives(StateUtils.getMoveSource(a), StateUtils.getMoveDestination(a), detectiveLocations);
                int scoreB = evaluator.spreadHeuristicDetectives(StateUtils.getMoveSource(b), StateUtils.getMoveDestination(b), detectiveLocations);
                return Integer.compare(scoreA, scoreB); // higher spread first
            });
        } else {
            moves.sort((a, b) -> {
                int distA = evaluator.catchHeuristicDetectives(StateUtils.getMoveDestination(a), node.getMrXLocation());
                int distB = evaluator.catchHeuristicDetectives(StateUtils.getMoveDestination(b), node.getMrXLocation());
                return Integer.compare(distA, distB); // ascending order
            });
        }

        Move killer = killerMovesDetectives.get(depth);
        if (killer != null && moves.contains(killer)) {
            moves.remove(killer);
            moves.add(0, killer);
        }

        return moves.stream().limit(MOVE_LIMIT).toList();
    }


    private int maximiseMrX(Node node, int depth, int alpha, int beta) {
        int maxEval = Integer.MIN_VALUE;

        for (Move move : sortTopMovesMrX(node, depth)) {
            Board.GameState childState = node.getState().advance(move);
            Node child = new Node(childState);
            nodeCount++;
            child.setMrXLocation(StateUtils.getMoveDestination(move));

            int eval = minimax(child, depth - 1, alpha, beta);
            maxEval = Math.max(maxEval, eval);
            alpha = Math.max(alpha, eval);
            if (beta <= alpha) {
                killerMovesMrX.put(depth, move);
                break;
            }

        }

        return maxEval;
    }

    private int minimiseDetectives(Node node, int depth, int alpha, int beta) {
        int minEval = Integer.MAX_VALUE;

        for (Move move : sortTopMovesDetectives(node, depth)) {
            Board.GameState childState = node.getState().advance(move);
            Node child = new Node(childState);
            nodeCount++;
            child.setMrXLocation(node.getMrXLocation());

            int eval = minimax(child, depth - 1, alpha, beta);
            minEval = Math.min(minEval, eval);
            beta = Math.min(beta, eval);
            if (beta <= alpha) {
                killerMovesDetectives.put(depth, move);
                break;
            }
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
            nodeCount++;
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
            Node child = new Node(rootNode.getState().advance(move));
            nodeCount++;
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
