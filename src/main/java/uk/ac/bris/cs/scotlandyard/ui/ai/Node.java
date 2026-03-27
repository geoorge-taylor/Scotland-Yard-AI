package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;

import java.util.ArrayList;
import java.util.List;


public class Node {
    private final Board.GameState state;
    private final boolean isMrXTurn;
    private int evaluation;
    private final ImmutableSet<Move> legalMoves;
    private final List<Node> children;

    // the two possible types of moves that lead to a node
    private ImmutableList<Move> detectiveMoves;
    private Move mrXMove;

    public Node(Board.GameState state, boolean isMrXTurn) {
        this.legalMoves = state.getAvailableMoves();
        this.state = state;
        this.isMrXTurn = isMrXTurn;
        this.children = new ArrayList<>();
    }

    // GETTERS
    public Board.GameState getState() { return state; }
    public ImmutableSet<Move> getLegalMoves() { return legalMoves; }
    public List<Node> getChildren() { return children; }
    public Move getMrXMove() { return mrXMove; }

    // SETTERS
    public void setEvaluation(int newEvaluation) { evaluation = newEvaluation; }
    public void setDetectiveMoves(ImmutableList<Move> newDetectiveMoves) { detectiveMoves = newDetectiveMoves; }
    public void setMrXMove(Move newMrXMove) { mrXMove = newMrXMove; }


    // HELPERS
    public boolean isMrXTurn() { return isMrXTurn; }
    public void addChild(Node node) { children.add(node); }
    public boolean isTerminal() { return !state.getWinner().isEmpty() || legalMoves.isEmpty(); }

    @Override
    public String toString() {
        return String.format("[Node %s] - Evaluation: %d", isMrXTurn, evaluation);
    }
}
