package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Piece;

import java.util.*;
import java.util.stream.Collectors;

public class StateEvaluator {
    public static final int CLOSEST_MULTIPLIER = 100;
    private Map<Integer, Map<Integer, Integer>> distanceMap;

    public StateEvaluator(Map<Integer, Map<Integer, Integer>> distanceMap) {
        this.distanceMap = distanceMap;
    }

    // TODO: make it take into account things like mobility and distance from prev position penalty
    public int evaluateNode(Node node) {
        Board.GameState state = node.getState();
        int mrXLocation = node.getMrXLocation();
        Set<Integer> detectiveLocations = StateUtils.getDetectiveLocations(state);

        // if mrX node, then score would be based on how FAR mrX is away from the detectives (i.e further away = better)
        // If detective node, then score would be based on how CLOSE detectives are away from mrX LAST KNOWN location
        Map<Integer, Integer> detectiveDistances = getDistancesToDetectives(mrXLocation, detectiveLocations);
        int minDistance = detectiveDistances.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int sumDistance = detectiveDistances.values().stream().mapToInt(Integer::intValue).sum();
        return (minDistance * CLOSEST_MULTIPLIER) + sumDistance;
    }

    public Map<Integer, Integer> getDistancesToDetectives(int mrXLocation, Set<Integer> detectiveLocations) {
        Map<Integer, Integer> distancesFromMrX = distanceMap.get(mrXLocation);
        Map<Integer, Integer> result = new HashMap<>();

        for (Integer detective : detectiveLocations) {
            int dist = distancesFromMrX.getOrDefault(detective, Integer.MAX_VALUE);
            result.put(detective, dist);
        }

        return result;
    }

    public int heuristicMrX(int newMrXLocation, Set<Integer> detectiveLocations) {
        Map<Integer, Integer> distances = distanceMap.get(newMrXLocation);
        int closest = Integer.MAX_VALUE;

        for (Integer detective : detectiveLocations) {
            int d = distances.getOrDefault(detective, Integer.MAX_VALUE);
            if (d < closest) closest = d;
        }

        return closest; // higher = better
    }

    public int heuristicDetectives(int newDetectiveLocation, int mrXLocation) {
        int dist = distanceMap.get(newDetectiveLocation).getOrDefault(mrXLocation, Integer.MAX_VALUE);
        return -dist; // smaller distance = higher priority
    }
}
