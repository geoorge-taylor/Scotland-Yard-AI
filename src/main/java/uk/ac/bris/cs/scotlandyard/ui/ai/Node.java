package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

public class Node {
    private final Board.GameState state;
    private Move priorDetectiveMove;
    private Move priorMrXMove;
    private int mrXLocation;

    public Node(Board.GameState state) {
        this.state = state;
    }

    // GETTERS
    public Board.GameState getState() { return state; }
    public Move getPriorMrXMove() { return priorMrXMove; }
    public Move getPriorDetectiveMove() { return priorMrXMove; }
    public int getMrXLocation() {return mrXLocation; }

    // SETTERS
    //public void setEvaluation(int newEvaluation) { evaluation = newEvaluation; }
    public void setPriorDetectiveMove(Move newDetectiveMove) { priorDetectiveMove = newDetectiveMove; }
    public void setPriorMrXMove(Move newMrXMove) { priorMrXMove = newMrXMove; }
    public void setMrXLocation(int newLocation) { mrXLocation = newLocation; }

    // HELPERS
    public boolean isTerminal() { return !state.getWinner().isEmpty() || state.getAvailableMoves().isEmpty(); }

    @Override
    public String toString() { return "Node"; }
}
