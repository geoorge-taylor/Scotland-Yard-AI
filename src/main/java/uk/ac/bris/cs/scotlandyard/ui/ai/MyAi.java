package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import jakarta.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MyAi implements Ai {
	public static final int treeDepthLimit = 3;
	public static final int BFSDepthLimit = 10;

	@Nonnull @Override public String name() { return "MAGIC AI"; }

	private boolean isMrXTurn(Board board) {
		return board.getAvailableMoves().stream()
				.allMatch(move -> move.commencedBy().isMrX());
	}

	private ImmutableSet<Piece.Detective> getDetectives(Board board) {
		return board.getPlayers().stream()
				.filter(p -> p instanceof Piece.Detective)
				.map(p -> (Piece.Detective) p)
				.collect(ImmutableSet.toImmutableSet());
	}

	private int getLastKnownMrXLocation(Board board) {
		List<LogEntry> log = board.getMrXTravelLog();

		// Iterate backwards from the most recent entry
		for (int i = log.size() - 1; i >= 0; i--) {
			if (log.get(i).location().isPresent()) {
				return log.get(i).location().get();
			}
		}

		return 0;
	}

	// TODO: Make this record all detectives with just one method call -- so inefficient !!
	private int getShortestDistanceToDetective(ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph, int detectiveLocation, int mrXLocation) {

		if (detectiveLocation == mrXLocation) return 0;

		Queue<Integer> queue = new ArrayDeque<>();
		Set<Integer> visited = new HashSet<>();
		Map<Integer, Integer> distances = new HashMap<>();

		queue.add(mrXLocation);
		visited.add(mrXLocation);
		distances.put(mrXLocation, 0);

		while (!queue.isEmpty()) {
			int current = queue.poll();
			int currentDist = distances.get(current);
			if (currentDist >= BFSDepthLimit) continue;

			for (Integer neighbor : graph.adjacentNodes(current)) {
				if (visited.contains(neighbor)) continue;

				visited.add(neighbor);
				distances.put(neighbor, currentDist + 1);
				queue.add(neighbor);

				if (neighbor == detectiveLocation) {
					return currentDist + 1; // found detective
				}
			}
		}

		return Integer.MAX_VALUE;
	}

	private Board.GameState applyDetectiveMoveCombination(Board.GameState board, ImmutableList<Move> moves) {
		Board.GameState newBoard = board;
		for (Move move : moves) {
			newBoard = newBoard.advance(move);
		}
		return newBoard;
	}

	private ImmutableSet<ImmutableList<Move>> getDetectiveMoveCombinations(Board.GameState board) {
		// branch ends when the game ends or all detectives have now moved
		if (!board.getWinner().isEmpty() || isMrXTurn(board)) {
			return ImmutableSet.of(ImmutableList.of());
		}

		Set<ImmutableList<Move>> combinations = new HashSet<>();
		for (Move move : board.getAvailableMoves()) {
			Board.GameState nextBoard = board.advance(move);
			ImmutableSet<ImmutableList<Move>> branches = getDetectiveMoveCombinations(nextBoard);

			// when building back up, append all sub-branches to the combinations
			for (ImmutableList<Move> branch : branches) {
				combinations.add(ImmutableList.<Move>builder()
						.add(move)
						.addAll(branch)
						.build());
			}
		}

		return ImmutableSet.copyOf(combinations);
	}

	private int evaluateNode(Node node) {
		Board.GameState board = node.getState();
		ImmutableSet<Piece.Detective> detectives = getDetectives(board);
		int evaluatedScore = 0;

		// are we supposed to be able to get mrX location?
		// logic for if is a detective node not implemented yet
		if (node.isMrXTurn()) {
			for (Piece.Detective detective : detectives) {
				int location = board.getDetectiveLocation(detective).get();
				int distance = getShortestDistanceToDetective(board.getSetup().graph, location, 0);
				evaluatedScore = Math.min(distance, evaluatedScore);
			}
		}

		return evaluatedScore;
	}

	private void generateNodeChildren(Node node) {
		Board.GameState board = node.getState();
		if (!node.getChildren().isEmpty() || node.isTerminal()) {
			return;
		}

		if (node.isMrXTurn()) {
			for (Move move : node.getLegalMoves()) {
				Board.GameState nextBoard = board.advance(move);
				Node child = new Node(nextBoard, false);
				node.addChild(child);
				node.setMrXMove(move);
			}
		} else {
			// One child per full detective response
			ImmutableSet<ImmutableList<Move>> detectiveResponses =
					getDetectiveMoveCombinations(board);

			for (ImmutableList<Move> response : detectiveResponses) {
				Board.GameState nextBoard = applyDetectiveMoveCombination(board, response);
				Node child = new Node(nextBoard, true);
				child.setDetectiveMoves(response);
				node.getChildren().add(child);
			}
		}
	}

	private int minimax(Node node, int depth, int alpha, int beta) {
		if (depth == 0 || node.isTerminal()) return evaluateNode(node);
		// lazily generate node children only when minimax is called on it
		generateNodeChildren(node);

		if (node.isMrXTurn()) {
			int maxEval = Integer.MIN_VALUE;
			for (Node child : node.getChildren()) {
				int eval = minimax(child, depth - 1, alpha, beta);
				maxEval = Math.max(maxEval, eval);
				alpha = Math.max(alpha, eval);
				if (beta <= alpha) break;
			}

			node.setEvaluation(maxEval);
			return maxEval;

		} else {
			int minEval = Integer.MAX_VALUE;
			for (Node child : node.getChildren()) {
				int eval = minimax(child, depth - 1, alpha, beta);
				minEval = Math.min(minEval, eval);
				beta = Math.min(beta, eval);
				if (beta <= alpha) break;
			}

			node.setEvaluation(minEval);
			return minEval;
		}
	}

	private Move pickBestMrXMove(Move defaultMove, Node rootNode, Pair<Long, TimeUnit> timeoutPair) {
		int alpha = Integer.MIN_VALUE;
		int beta = Integer.MAX_VALUE;
		int bestScore = Integer.MIN_VALUE;
		Move bestMove = defaultMove;

		for (Node child : rootNode.getChildren()) {
			int score = minimax(child, treeDepthLimit - 1, alpha, beta);
			if (score > bestScore) {
				bestMove = child.getMrXMove();
				bestScore = score;
			}
		}

		return bestMove;
	}

	private ImmutableMap<ScotlandYard.Ticket, Integer> getTickets(Board board, Piece piece) {
		Map<ScotlandYard.Ticket, Integer> ticketMap = new HashMap<>();
		Board.TicketBoard ticketBoard = board.getPlayerTickets(piece).get();

		for (ScotlandYard.Ticket ticket : ScotlandYard.Ticket.values()) {
			ticketMap.put(ticket, ticketBoard.getCount(ticket));
		}

		return ImmutableMap.copyOf(ticketMap);
	}

	private ImmutableList<Player> extractDetectives(Board board) {
		List<Player> detectives = new ArrayList<>();
		for (Piece piece : board.getPlayers()) {
			if (piece.isDetective()) {
				int location = board.getDetectiveLocation((Piece.Detective) piece).get();
				detectives.add(new Player(piece, getTickets(board, piece), location));
			}
		}
		return ImmutableList.copyOf(detectives);
	}

	private Player extractMrX(Board board, int mrXCurrentLocation) {
		Piece mrXPiece = board.getPlayers().stream()
				.filter(Piece::isMrX)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("MrX not found on board"));

		return new Player(mrXPiece, getTickets(board, mrXPiece), mrXCurrentLocation);
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		// TODO: Extract the players from what information we have with the board;
		int lastMrXLocation = getLastKnownMrXLocation(board);

		Player mrX = extractMrX(board, lastMrXLocation);
		ImmutableList<Player> detectives = extractDetectives(board);

		Board.GameState simulationBoard = MyGameStateFactory.a(board.getSetup(), mrX, detectives);
		var moves = board.getAvailableMoves().asList();
		Move bestMove = moves.get(new Random().nextInt(moves.size()));

		// set up start of the game tree
		Node rootNode = new Node(simulationBoard, isMrXTurn(board));
		generateNodeChildren(rootNode);

		if (isMrXTurn(board)) {
			bestMove = pickBestMrXMove(bestMove, rootNode, timeoutPair);
		} else {
			System.out.println("No implementation for detective move!");
		}

		return bestMove;
	}
}
