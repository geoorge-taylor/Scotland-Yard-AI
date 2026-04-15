package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Piece;

import java.util.Map;

public record StateKey (
    int mrXLocation,
    Map<Piece, Integer> detectiveLocations,
    boolean isMrXTurn,
    int remainingMoves// Or turn number
) {}
