package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Piece;

import java.util.*;
import java.util.stream.Collectors;

public class StateEvaluator {
    private final Map<CacheKey, Map<Integer, Integer>> bfsCache = new HashMap<>();
    public static final int BFS_LIMIT = 10;

    public int evaluateNode(Node node) {
        Board.GameState state = node.getState();
        int mrXLocation = node.getMrXLocation();

        ImmutableSet<Piece.Detective> detectives = StateUtils.extractDetectivePieces(state);
        Set<Integer> detectiveLocations = detectives.stream()
                .map(p -> state.getDetectiveLocation(p).get())
                .collect(Collectors.toSet());

        // if mrX node, then score would be based on how FAR mrX is away from the detectives (i.e further away = better)
        // If detective node, then score would be based on how CLOSE detectives are away from mrX LAST KNOWN location
        Map<Integer, Integer> detectiveDistances = getDistancesFromMrXToDetectives(state, detectiveLocations, mrXLocation);
        return detectiveDistances.values().stream().mapToInt(Integer::intValue).sum();
    }

    private Map<Integer, Integer> getDistancesFromMrXToDetectives(Board.GameState state, Set<Integer> detectiveLocations, int mrXLocation) {
        var graph = state.getSetup().graph;
        CacheKey key = new CacheKey(mrXLocation, detectiveLocations);
        if (bfsCache.containsKey(key)) return bfsCache.get(key);

        int detectiveLocationsSize = detectiveLocations.size();
        Map<Integer, Integer> detectiveDistances = new HashMap<>();
        Map<Integer, Integer> nodeDistances = new HashMap<>();
        Queue<Integer> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();

        queue.add(mrXLocation);
        visited.add(mrXLocation);
        nodeDistances.put(mrXLocation, 0);

        if (detectiveLocations.contains(mrXLocation)) {
            detectiveDistances.put(mrXLocation, 0);
            if (detectiveDistances.size() == detectiveLocations.size())
                return detectiveDistances; // all found
        }

        // while there are still nodes left to visit and distances found to detectives is less than the total number of distances to find
        while (!queue.isEmpty() && detectiveDistances.size() < detectiveLocationsSize) {
            int current = queue.poll();
            int currentDist = nodeDistances.get(current);
            if (currentDist >= BFS_LIMIT) {
                System.out.println("Hit BFS Limit");
                continue;
            }

            for (Integer neighbor : graph.adjacentNodes(current)) {
                if (visited.contains(neighbor)) continue;

                visited.add(neighbor);
                nodeDistances.put(neighbor, currentDist + 1);
                queue.add(neighbor);

                // record if this neighbor and its distance to mrX since it contains a detective
                if (detectiveLocations.contains(neighbor) && !detectiveDistances.containsKey(neighbor)) {
                    detectiveDistances.put(neighbor, currentDist + 1);
                }
            }
        }

        bfsCache.put(key, detectiveDistances);
        return detectiveDistances;
    }
}
