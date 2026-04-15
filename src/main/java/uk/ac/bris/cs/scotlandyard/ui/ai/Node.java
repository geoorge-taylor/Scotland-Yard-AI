package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

public class Node {
    private final Board.GameState state;
    private int mrXLocation;

    public Node(Board.GameState state) {
        this.state = state;
    }

    public Board.GameState getState() { return state; }
    public int getMrXLocation() {return mrXLocation; }
    public void setMrXLocation(int newLocation) { mrXLocation = newLocation; }
    public boolean isTerminal() { return !state.getWinner().isEmpty() || state.getAvailableMoves().isEmpty(); }

    @Override
    public String toString() { return "Node"; }
}
