package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.ietf.jgss.GSSManager;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class StateUtils {
    private static final int DEFAULT_MRX_LOCATION = 101;

    public static boolean isMrXTurn(Board board) {
        return board.getAvailableMoves().stream()
                .allMatch(move -> move.commencedBy().isMrX());
    }

    public static ImmutableSet<Piece.Detective> extractDetectivePieces(Board board) {
        return board.getPlayers().stream()
                .filter(Piece.Detective.class::isInstance)
                .map(Piece.Detective.class::cast)
                .collect(ImmutableSet.toImmutableSet());
    }

    public static Piece.MrX extractMrXPiece(Board board) {
        return board.getPlayers().stream()
                .filter(Piece.MrX.class::isInstance)
                .map(Piece.MrX.class::cast)
                .findFirst()
                .orElseThrow();
    }

    public static int getLastKnownMrXLocation(Board board) {
//        if (isMrXTurn(board)) throw new IllegalStateException("Is MrX Turn!");
//        return board.getMrXTravelLog().stream()
//                .map(LogEntry::location)
//                .filter(Optional::isPresent)
//                .map(Optional::get)
//                .findFirst().orElse(DEFAULT_MRX_LOCATION);
        List<LogEntry> log = board.getMrXTravelLog();
        for (int i = log.size() - 1; i >= 0; i--) {
            if (log.get(i).location().isPresent()) return log.get(i).location().get();
        }
        return DEFAULT_MRX_LOCATION;
    }

    public static int getActualMrXLocation(Board board) {
        if (!isMrXTurn(board)) throw new IllegalStateException("Not MrX Turn!");
        if (board.getAvailableMoves().isEmpty()) return 101;
        else return board.getAvailableMoves().stream()
                .findFirst().orElseThrow().source();
    }

    public static boolean hasMrXRevealedLocation(Board board) {
        if (isMrXTurn(board)) throw new IllegalStateException("Is MrX Turn!");
        return board.getMrXTravelLog().isEmpty(); // true if not revealed
    }

    public static int getMoveDestination(Move move) {
        if (move instanceof Move.SingleMove singeMove) return singeMove.destination;
        if (move instanceof Move.DoubleMove doubleMove) return doubleMove.destination2;
        throw new IllegalStateException("Unknown move type");
    }

    public static int getMoveSource(Move move) {
        if (move instanceof Move.SingleMove singeMove) {
            return singeMove.source();
        }
        return 0;
    }

    public static ImmutableMap<ScotlandYard.Ticket, Integer> getTickets(Board board, Piece piece) {
        Map<ScotlandYard.Ticket, Integer> ticketMap = new HashMap<>();
        Board.TicketBoard ticketBoard = board.getPlayerTickets(piece).get();

        for (ScotlandYard.Ticket ticket : ScotlandYard.Ticket.values()) {
            ticketMap.put(ticket, ticketBoard.getCount(ticket));
        }

        return ImmutableMap.copyOf(ticketMap);
    }

    public static ImmutableList<Player> extractDetectivePlayers(Board board) {
        List<Player> detectives = new ArrayList<>();
        for (Piece.Detective piece : extractDetectivePieces(board)) {
            int location = board.getDetectiveLocation(piece).get();
            detectives.add(new Player(piece, getTickets(board, piece), location));
        }
        return ImmutableList.copyOf(detectives);
    }

    public static Player extractMrXPlayer(Board board) {
        Piece.MrX mrXPiece = extractMrXPiece(board);
        int mrXCurrentLocation;
        if (isMrXTurn(board)) { mrXCurrentLocation = getActualMrXLocation(board); }
        else { mrXCurrentLocation = getLastKnownMrXLocation(board); }
        return new Player(mrXPiece, getTickets(board, mrXPiece), mrXCurrentLocation);
    }

    public static Set<Integer> getDetectiveLocations(Board.GameState state) {
        ImmutableSet<Piece.Detective> detectives = StateUtils.extractDetectivePieces(state);
        return detectives.stream()
                .map(p -> state.getDetectiveLocation(p).get())
                .collect(Collectors.toSet());
    }

    public static Map<Integer, Integer> bfsAllDistances(int start, Board board) {
        var graph = board.getSetup().graph;
        Map<Integer, Integer> distances = new HashMap<>();
        Queue<Integer> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);
        distances.put(start, 0);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int currentDist = distances.get(current);

            for (Integer neighbor : graph.adjacentNodes(current)) {
                if (visited.contains(neighbor)) continue;

                visited.add(neighbor);
                distances.put(neighbor, currentDist + 1);
                queue.add(neighbor);
            }
        }

        return distances;
    }

    public static Map<Integer, Map<Integer, Integer>> buildDistanceMap(Board board) {
        Map<Integer, Map<Integer, Integer>> distanceMap = new HashMap<>();
        var graph = board.getSetup().graph;

        for (Integer node : graph.nodes()) {
            distanceMap.put(node, bfsAllDistances(node, board));
        }

        return distanceMap;
    }
}
