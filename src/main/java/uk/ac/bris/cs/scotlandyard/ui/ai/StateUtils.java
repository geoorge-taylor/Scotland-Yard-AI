package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.*;

import java.util.*;

public class StateUtils {

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
        if (isMrXTurn(board)) throw new IllegalStateException("Is MrX Turn!");
        return board.getMrXTravelLog().stream()
                .map(LogEntry::location)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst().orElseThrow();
    }

    public static int getActualMrXLocation(Board board) {
        if (!isMrXTurn(board)) throw new IllegalStateException("Not MrX Turn!");
        if (board.getAvailableMoves().isEmpty()) return 101;
        else return board.getAvailableMoves().stream()
                .findFirst().orElseThrow().source();
    }

    public static int getMoveDestination(Move move) {
        if (move instanceof Move.SingleMove singeMove) return singeMove.destination;
        if (move instanceof Move.DoubleMove doubleMove) return doubleMove.destination2;
        throw new IllegalStateException("Unknown move type");
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

    // Compute a map of all nodes to how far away the nearest detective is away from them
    public static Map<Integer, Integer> multiSourceBFS(Board board) {
        var graph = board.getSetup().graph;

        Map<Integer, Integer> nodeDistances = new HashMap<>();
        Queue<Integer> queue = new ArrayDeque<>();

        for (Piece.Detective det : StateUtils.extractDetectivePieces(board)) {
            board.getDetectiveLocation(det).ifPresent(location -> {
                nodeDistances.put(location, 0);
                queue.add(location);
            });
        }

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int currentDist = nodeDistances.get(current);

            for (Integer neighbor : graph.adjacentNodes(current)) {
                if (!nodeDistances.containsKey(neighbor)) {
                    nodeDistances.put(neighbor, currentDist + 1);
                    queue.add(neighbor);
                }
            }
        }

        return nodeDistances;
    }
}
