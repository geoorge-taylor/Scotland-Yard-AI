package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SearchEngine {
    private static final int TREE_DEPTH = 5;
    private final StateEvaluator evaluator;
    private final Map<Integer, Integer> distanceMap;

    public SearchEngine(StateEvaluator evaluator, Map<Integer, Integer> distanceMap) {
        this.evaluator = evaluator;
        this.distanceMap = distanceMap;
    }

    private int minimax(Node node, int depth, int alpha, int beta) {
        if (depth == 0 || node.isTerminal()) {
            int eval = evaluator.evaluateNode(node);
            node.setEvaluation(eval);
            return eval;
        }

        return StateUtils.isMrXTurn(node.getState())
                ? maximiseMrX(node, depth, alpha, beta)
                : minimiseDetectives(node, depth, alpha, beta);
    }

    private int maximiseMrX(Node node, int depth, int alpha, int beta) {
        //List<Move> moves = new ArrayList<>(node.getState().getAvailableMoves());

//        moves.sort((a, b) -> {
//            int distA = distanceMap.getOrDefault(StateUtils.getMoveDestination(a), 0);
//            int distB = distanceMap.getOrDefault(StateUtils.getMoveDestination(b), 0);
//            return Integer.compare(distB, distA); // descending order
//        });

        int maxEval = Integer.MIN_VALUE;

        for (Move move : node.getState().getAvailableMoves()) {
            Board.GameState childState = node.getState().advance(move);
            Node child = new Node(childState);
            child.setPriorMrXMove(move);
            child.setMrXLocation(StateUtils.getMoveDestination(move));

            int eval = minimax(child, depth - 1, alpha, beta);
            maxEval = Math.max(maxEval, eval);
            alpha = Math.max(alpha, eval);
            if (beta <= alpha) break;

        }

        node.setEvaluation(maxEval);
        return maxEval;
    }

    private int minimiseDetectives(Node node, int depth, int alpha, int beta) {
        int minEval = Integer.MAX_VALUE;

        for (Move move : node.getState().getAvailableMoves()) {
            Board.GameState childState = node.getState().advance(move);
            Node child = new Node(childState);
            child.setPriorDetectiveMove(move);
            child.setMrXLocation(node.getMrXLocation());

            int eval = minimax(child, depth - 1, alpha, beta);
            minEval = Math.min(minEval, eval);
            beta = Math.min(beta, eval);
            if (beta <= alpha) break;
        }

        node.setEvaluation(minEval);
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

        System.out.println("The best score was: " + bestScore);
        System.out.println("The best move is thus: " + bestMove);
        return bestMove;
    }
}
