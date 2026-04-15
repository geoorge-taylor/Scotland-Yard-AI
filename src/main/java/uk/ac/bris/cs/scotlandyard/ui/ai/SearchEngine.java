package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class SearchEngine {
    private static final int TREE_DEPTH = 7;
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

    private int minimax(Node node, int depth, int alpha, int beta, long deadline) {
        if (System.currentTimeMillis() > deadline) {
            return evaluator.evaluateNodeCatch(node);
        }

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
                ? maximiseMrX(node, depth, alpha, beta, deadline)
                : minimiseDetectives(node, depth, alpha, beta, deadline);

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
                return Integer.compare(scoreB, scoreA); // higher spread first
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


    private int maximiseMrX(Node node, int depth, int alpha, int beta, long deadline) {
        int maxEval = Integer.MIN_VALUE;

        for (Move move : sortTopMovesMrX(node, depth)) {
            if (System.currentTimeMillis() > deadline) break;
            Board.GameState childState = node.getState().advance(move);
            Node child = new Node(childState);
            nodeCount++;
            child.setMrXLocation(StateUtils.getMoveDestination(move));

            int eval = minimax(child, depth - 1, alpha, beta, deadline);
            maxEval = Math.max(maxEval, eval);
            alpha = Math.max(alpha, eval);
            if (beta <= alpha) {
                killerMovesMrX.put(depth, move);
                break;
            }

        }

        return maxEval;
    }

    private int minimiseDetectives(Node node, int depth, int alpha, int beta, long deadline) {
        int minEval = Integer.MAX_VALUE;

        for (Move move : sortTopMovesDetectives(node, depth)) {
            if (System.currentTimeMillis() > deadline) break;
            Board.GameState childState = node.getState().advance(move);
            Node child = new Node(childState);
            nodeCount++;
            child.setMrXLocation(node.getMrXLocation());

            int eval = minimax(child, depth - 1, alpha, beta, deadline);
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
        Move bestMove = defaultMove;
        long startTime = System.currentTimeMillis();
        long durationMillis = timeoutPair.right().toMillis(timeoutPair.left());
        long deadline = startTime + durationMillis - 500;

        for (int currentDepth = 1; currentDepth <= TREE_DEPTH; currentDepth++) {
            Move bestMoveAtThisDepth = null;
            int bestScoreAtThisDepth = Integer.MIN_VALUE;
            int alpha = Integer.MIN_VALUE;
            int beta = Integer.MAX_VALUE;

            System.out.println("Reached depth for mrX move: " + currentDepth);

            for (Move move : availableMoves) {
                if (System.currentTimeMillis() > deadline) break;

                Node child = new Node(rootNode.getState().advance(move));
                child.setMrXLocation(StateUtils.getMoveDestination(move));

                int score = minimax(child, currentDepth - 1, alpha, beta, deadline);

                if (score > bestScoreAtThisDepth) {
                    bestScoreAtThisDepth = score;
                    bestMoveAtThisDepth = move;
                }
                alpha = Math.max(alpha, bestScoreAtThisDepth);
            }


            if (System.currentTimeMillis() <= deadline && bestMoveAtThisDepth != null) {
                bestMove = bestMoveAtThisDepth;
            } else {
                break;
            }
        }
        return bestMove;
    }

    public Move pickBestDetectiveMove(ImmutableSet<Move> availableMoves, Move defaultMove, Node rootNode, Pair<Long, TimeUnit> timeoutPair) {

        Move bestMove = defaultMove;

        long startTime = System.currentTimeMillis();
        long durationMillis = timeoutPair.right().toMillis(timeoutPair.left());
        long deadline = startTime + durationMillis - 500;

        for (int currentDepth = 1; currentDepth <= TREE_DEPTH; currentDepth++) {
            Move bestMoveAtThisDepth = null;
            int bestScoreAtThisDepth = Integer.MAX_VALUE;
            int alpha = Integer.MIN_VALUE;
            int beta = Integer.MAX_VALUE;

            System.out.println("Reached depth for detective move: " + currentDepth);

            for (Move move : availableMoves) {
                if (System.currentTimeMillis() > deadline) break;
                Node child = new Node(rootNode.getState().advance(move));
                nodeCount++;
                child.setMrXLocation(rootNode.getMrXLocation());
                int score = minimax(child, currentDepth - 1, alpha, beta, deadline);

                if (score < bestScoreAtThisDepth) {
                    bestScoreAtThisDepth = score;
                    bestMoveAtThisDepth = move;
                }

                beta = Math.min(beta, bestScoreAtThisDepth);
            }
            if (System.currentTimeMillis() <= deadline && bestMoveAtThisDepth != null) {
                bestMove = bestMoveAtThisDepth;
            } else {
                break;
            }
        }
        return bestMove;
    }
}
