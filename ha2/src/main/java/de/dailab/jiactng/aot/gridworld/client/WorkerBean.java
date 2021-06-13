package de.dailab.jiactng.aot.gridworld.client;

import de.dailab.jiactng.agentcore.AbstractAgentBean;
import de.dailab.jiactng.agentcore.action.Action;
import de.dailab.jiactng.agentcore.comm.ICommunicationAddress;
import de.dailab.jiactng.agentcore.comm.ICommunicationBean;
import de.dailab.jiactng.agentcore.comm.message.JiacMessage;
import de.dailab.jiactng.agentcore.knowledge.IFact;
import de.dailab.jiactng.agentcore.ontology.AgentDescription;
import de.dailab.jiactng.agentcore.ontology.IAgentDescription;
import de.dailab.jiactng.aot.gridworld.messages.*;
import de.dailab.jiactng.aot.gridworld.model.*;
import org.sercho.masp.space.event.SpaceEvent;
import org.sercho.masp.space.event.SpaceObserver;
import org.sercho.masp.space.event.WriteCallEvent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkerBean extends AbstractAgentBean {

	private boolean active;								// worker only active if involved in game
	private boolean previousMoveValid;					// referee (server) approved last move
	private boolean atTarget;							// worker is at order target
	private int myTurn;									// game turn
	private String myId;								// Classes inheriting from Element--e.g., Worker, Order-- have String IDs
	private Integer gameId; 							// current game id
	private Map<Position, Integer> currentOrderPosition;// list of all target order positions with their deadline
	private Position myPosition;						// my current position on grid
	private Position gridSize; 							// the position of "biggest" grid field possible
	private List<Position> obstacles;					// list of all obstacles - received at beginning of game
	private List<Position> myMoves;						// all Moves in correct order to execute
	private Graph gridGraph;							// Graph for distance calculation

	@Override
	public void doStart() throws Exception {
		super.doStart();
		this.active = false;
		this.myTurn = 2;
		this.previousMoveValid = true;
		this.atTarget = false;
		this.currentOrderPosition = new HashMap<>();
		this.myMoves = new ArrayList<>();
		memory.attach(new WorkerBean.MessageObserver(), new JiacMessage());
		log.info("Starting worker agent with id: " + thisAgent.getAgentId());
	}

	@Override
	public void execute() {

		/* If there is an open task, do move */
		/* Make sure last move was valid */
		if (previousMoveValid){
			WorkerAction myMove = this.doMove();
			if(myMove == null) return;
			this.sendMoveToRef(myMove);
			System.out.println("----------------------WORKER EXECUTING NEXT MOVE: " + myMove + " ---------------------------");
			System.out.println("----------------------MY POSITION: " + this.myPosition + "----------------------------------"); // ORDER POSITION: " + this.currentOrder.position + "------------");
			System.out.println("----------------------AT TARGET: " + this.atTarget + "--------------------------.-----------");
		}
		else { System.out.println("Previous move not approved."); }

		this.myTurn++;
	}

	/* calculates the distance from one position to another (considering the obstacles) and returns number of moves necessary to get there. */
	private int calculateDistance (Position firstPosition, Position secondPosition) {
		int myNode = positionToNode(firstPosition);
		int orderNode = positionToNode(secondPosition);

		// new bfs, starting from my position
		BreadthFirstPaths bfs = new BreadthFirstPaths(this.gridGraph, myNode);
		int distance = -1;
		if (bfs.hasPathTo(orderNode)){
			distance = bfs.distTo(orderNode);
		}
		return distance;
	}

	/* figures out if order is possible. If yes return success and distance to target, if not return fail */
	public void handleCheckDistance(CheckDistance cd){

		/* default message if worker declines */
		CheckDistanceResponse msg = new CheckDistanceResponse(cd.orderId, -1, Result.FAIL, null);
		ICommunicationAddress brokerAddress = this.getBrokerAddress();

		int currentDistance;
		int turn = this.myTurn;

		/* if target is too far away */
		if(calculateDistance(this.myPosition, cd.position) >= cd.deadline - turn - 3){
			this.sendMessage(brokerAddress, msg);
			return;
		}

		/* check if worker has an order already, if not calculate distance and respond */
		if(this.currentOrderPosition.size() == 0) {
			currentDistance = calculateDistance(this.myPosition, cd.position);
			msg = new CheckDistanceResponse(cd.orderId, currentDistance, Result.SUCCESS, null);
			this.sendMessage(brokerAddress, msg);
			return;
		}

		/* check if order is possible despite current order(s) */
		int spareTime = 1000000;
		Position positionTarget = new Position(-1, -1);
		for (Map.Entry<Position, Integer> deadlines : currentOrderPosition.entrySet()) {
			if(deadlines.getValue() - this.myTurn < spareTime)
				spareTime = deadlines.getValue() - this.myTurn-1;
				positionTarget = deadlines.getKey();
		}

		/* if we don't have time to take another order before or after */
		if(spareTime < cd.deadline - this.myTurn && cd.deadline - this.myTurn - this.myMoves.size() <= 0){
			this.sendMessage(brokerAddress, msg);
			return;
		}

		boolean before = spareTime >= cd.deadline - this.myTurn;
		boolean after = cd.deadline - this.myTurn - this.myMoves.size() > 0;

		/* order can be taken in deadline */
		while(before || after){
			if(before){
				if(calculateDistance(this.myPosition, cd.position) + calculateDistance(cd.position, positionTarget) <= spareTime){
					msg = new CheckDistanceResponse(cd.orderId, calculateDistance(this.myPosition, cd.position), Result.SUCCESS, null);
					this.sendMessage(brokerAddress, msg);
					return;
				}
				before = false;
			}
			if(after){
				if(calculateDistance(this.myPosition, positionTarget) + calculateDistance(positionTarget, cd.position) <= cd.deadline - this.myTurn){
					msg = new CheckDistanceResponse(cd.orderId, calculateDistance(this.myPosition, positionTarget) + calculateDistance(positionTarget, cd.position), Result.SUCCESS, null);
					this.sendMessage(brokerAddress, msg);
					return;
				}
				after = false;
			}
		}

		this.sendMessage(brokerAddress, msg);
	}

	/* calculates the moves from one position to another (considering the obstacles)and returns this path */
	private List<Position> calculateMoves (Position firstPosition, Position secondPosition) {

		int myNode = positionToNode(firstPosition);
		int targetNode = positionToNode(secondPosition);
		List<Position> moves = new ArrayList<>();

		// do bfs to all nodes
		BreadthFirstPaths bfs = new BreadthFirstPaths(this.gridGraph, myNode);
		if (!bfs.hasPathTo(targetNode)) {
			System.out.println("No path to order found!");
			return moves;
		}
		// get path to target node
		for (int node: bfs.pathTo(targetNode)){
			moves.add(nodeToPosition(node));
		}
		// pop current position (first element of list)
		moves.remove(0);
		return moves;
	}

	/* figures out if order is possible. If yes return true and set moves in myMoves */
	public boolean handleCheckMoves (AssignOrder ao){

		int turn = this.myTurn;
		/* if target is too far away */
		if(calculateDistance(this.myPosition, ao.targetPosition) >= ao.deadline - turn - 3){
			return false;
		}

		/* check if worker has an order already, if not calculate distance and respond */
		if(this.currentOrderPosition.size() == 0) {
			this.myMoves = calculateMoves(this.myPosition, ao.targetPosition);
			return true;
		}

		/* check if order is possible despite current order(s) */
		int spareTime = 1000000;
		Position positionTarget = new Position(-1, -1);
		for (Map.Entry<Position, Integer> deadlines : currentOrderPosition.entrySet()) {
			if(deadlines.getValue() - this.myTurn < spareTime)
				spareTime = deadlines.getValue() - this.myTurn;
			positionTarget = deadlines.getKey();
		}

		/* if we don't have time to take another order before or after */
		if(spareTime < ao.deadline - this.myTurn && ao.deadline - this.myTurn - this.myMoves.size() <= 0){
			return false;
		}

		boolean before = spareTime >= ao.deadline - this.myTurn;
		boolean after = ao.deadline - this.myTurn - this.myMoves.size() > 0;

		/* order can be taken in deadline */
		while(before || after){
			if(before){
				if(calculateDistance(this.myPosition, ao.targetPosition) + calculateDistance(ao.targetPosition, positionTarget) <= spareTime){
					List<Position> newList = new ArrayList<>();
					newList.addAll(calculateMoves(this.myPosition, ao.targetPosition));
					newList.addAll(calculateMoves(ao.targetPosition, positionTarget));
					this.myMoves = newList;
					return true;
				}
				before = false;
			}
			if(after){
				if(calculateDistance(this.myPosition, positionTarget) + calculateDistance(positionTarget, ao.targetPosition) <= ao.deadline - this.myTurn){
					List<Position> newList = new ArrayList<>();
					newList.addAll(calculateMoves(this.myPosition, positionTarget));
					newList.addAll(calculateMoves(positionTarget, ao.targetPosition));
					this.myMoves = newList;
					return true;
				}
				after = false;
			}
		}

		return false;
	}

	/* check if worker can accept assigned order */
	private void handleAssignOrder(AssignOrder ao) {

		AssignOrderConfirm msg = new AssignOrderConfirm();
		msg.orderId = ao.orderId;
		msg.workerId = this.myId;
		ICommunicationAddress brokerAddress = this.getBrokerAddress();
		if(handleCheckMoves(ao)) {
			System.out.println(this.myMoves);
			msg.state = Result.SUCCESS;
			this.currentOrderPosition.put(ao.targetPosition, ao.deadline);
		}
		else
			msg.state = Result.FAIL;

		this.initAcceptedOrder();
		this.sendMessage(brokerAddress, msg);
	}

	/* Do move towards order
	 * Side-effects: update myPosition and atTarget
	 * Returns executed move/order */
	private WorkerAction doMove() {

		if(!atTarget && this.myMoves.size() == 0){
			return null;
		}

		if(atTarget) {
			this.atTarget = false;
			return WorkerAction.ORDER;
		}

		WorkerAction nextMove = this.calculateNextMove();

		/* Execute move and update myPosition */
		assert this.myPosition != null;
		this.myPosition = this.myPosition.applyMove(nextMove);

		/* update atTarget */
		this.atTarget = false;
		for (Map.Entry<Position, Integer> iterate : this.currentOrderPosition.entrySet()) {
			if (this.myPosition.equals(iterate.getKey())) {
				this.atTarget = true;
				break;
			}
		}

		/* if atTarget = true: return Order, else nextMove */
		return nextMove;
	}

	/* calculates the next move */
	private WorkerAction calculateNextMove() {

		/* gets next move from list */
		Position move = this.myMoves.get(0);
		this.myMoves.remove(0);

		int targetX = move.x;
		int targetY = move.y;
		int ownX = this.myPosition.x;
		int ownY = this.myPosition.y;

		if(ownX == targetX){
			if(ownY > targetY){
				return WorkerAction.NORTH;
			}
			else {
				return WorkerAction.SOUTH;
			}
		}

		else if(ownX > targetX){
			return WorkerAction.WEST;
		}

		return WorkerAction.EAST;
	}

	/* Send executed move (N/S/E/W or Order) to Server */
	private void sendMoveToRef(WorkerAction myMove) {

		/* Construct Worker Message */
		WorkerMessage workerMsg = new WorkerMessage();
		workerMsg.workerId = this.myId;
		workerMsg.action = myMove;
		workerMsg.gameId = this.gameId;

		/* Get server address and send message */
		ICommunicationAddress serverAddress = this.getServerAddress();
		this.sendMessage(serverAddress, workerMsg);
	}

	// Create new graph for representing grid & obstacles
	// this.gridSize & this.obstacles need to be initialized before calling this method!
	private void createGraph() {
		int x = this.gridSize.x;
		int y = this.gridSize.y;
		Graph gridGraph = new Graph(x*y);

		// map obstacles to graph vertices
		List<Integer> obstacleNodes = new ArrayList<>();
		for (Position obstacle : this.obstacles){
			obstacleNodes.add(this.positionToNode(obstacle));
		}

		// add edges for grid
		for (int j=0; j<y; j++) {
			for (int i = 0; i < x; i++) {
				int v = i + j*x;
				// if at last column, don't add right edge
				if (i < x - 1) {
					// if edge to obstacle, don't add
					if (!(obstacleNodes.contains(v) || obstacleNodes.contains(v+1))) {
						gridGraph.addEdge(v, v + 1);
					}
				}
				// if at last row, don't add bottom edge
				if (j < y - 1) {
					// if edge to obstacle, don't add
					if (!(obstacleNodes.contains(v) || obstacleNodes.contains(v+x))) {
						gridGraph.addEdge(v, v + x);
					}
				}
			}
		}
		this.gridGraph = gridGraph;
	}

	private int positionToNode(Position position){
		return position.y * this.gridSize.x + position.x;
	}

	private Position nodeToPosition(int node){
		int posY = (int) Math.floor(node/gridSize.x);
		int posX = node % gridSize.x;
		return new Position(posX, posY);
	}

	private void handleWorkerConfirm(WorkerConfirm msg){
		/* Validate previous move */
		this.previousMoveValid = msg.state.equals(Result.SUCCESS);
	}

	private void handleOrderCompleted(OrderCompleted msg) {
		this.atTarget = false;
		this.currentOrderPosition.remove(msg.pos);
	}

	/* There are more Worker Agents available than actually used in the game.
	* When the server starts a new game, he chooses a number n of "initial workers",
	* and sends this info to the Broker. The Broker must then "activate" n of his Worker Agents,
	* so that they can become active in the game.
	* These are the Worker Agents that are now ready to play and take new orders.
	*
	* This function does initial game and worker setup
	* */
	private void activate(ActivateWorker msg){
		this.active = true;
		// class with info about myself, contains Position attribute
		Worker me = msg.activatedWorker;
		this.myPosition = me.position;
		this.gridSize = msg.gridSize;
		this.obstacles = msg.obstacles;
		this.myId = me.id;
		this.gameId = msg.gameId;
		// setup graph
		this.createGraph();
	}

	/* checks if worker is already at target when assigned */
	private void initAcceptedOrder(){
		/* Default previous move to true, check if already at target */
		this.previousMoveValid = true;
		this.atTarget = false;
		for (Map.Entry<Position, Integer> iterate : this.currentOrderPosition.entrySet()) {
			if (this.myPosition.equals(iterate.getKey())) {
				this.atTarget = true;
				break;
			}
		}
	}

	private void handleMessage(JiacMessage msg){
		Object payload = msg.getPayload();

		if (payload instanceof ActivateWorker) {
			this.activate((ActivateWorker) payload);
			System.out.println("Worker Agent " + thisAgent.getAgentId() + " activated: ready to accept orders!");
		}
		else if (this.active){
			if (payload instanceof AssignOrder ) {
				this.handleAssignOrder((AssignOrder) payload);
			}
			else if (payload instanceof WorkerConfirm) {
				this.handleWorkerConfirm((WorkerConfirm) payload);
			}
			else if (payload instanceof CheckDistance){
				this.handleCheckDistance((CheckDistance) payload);
			}
			else if (payload instanceof OrderCompleted) {
				this.handleOrderCompleted((OrderCompleted) payload);
			}
		}
	}

	/* Infrastructure Functions */
	/* Message Observer allows event based async reacting to incoming messages */
	private class MessageObserver implements SpaceObserver<IFact> {
		/* new id */

		@SuppressWarnings("rawtypes")
		@Override
		public void notify(SpaceEvent<? extends IFact> event) {
			if (event instanceof WriteCallEvent) {
				WriteCallEvent writeEvent = (WriteCallEvent) event;
				if (writeEvent.getObject() instanceof JiacMessage) {
					JiacMessage message = (JiacMessage) writeEvent.getObject();
					if (message.getPayload() instanceof GridMessage) {
						handleMessage(message);
						memory.remove(message);
					}
				}
			}
		}
	}

	private ICommunicationAddress getServerAddress() {
		ICommunicationAddress server = null;
		IAgentDescription serverAgent = thisAgent.searchAgent(new AgentDescription(null, "ServerAgent", null, null, null, null));
		if (serverAgent != null) {
			server = serverAgent.getMessageBoxAddress();
		} else {
			System.out.println("SERVER NOT FOUND!");
		}
		return server;
	}

	private ICommunicationAddress getBrokerAddress() {
		ICommunicationAddress broker = null;
		IAgentDescription brokerAgent = thisAgent.searchAgent(new AgentDescription(null, "BrokerAgent", null, null, null, null));
		if (brokerAgent != null) {
			broker = brokerAgent.getMessageBoxAddress();
		} else {
			System.out.println("BROKER NOT FOUND!");
		}
		return broker;
	}

	private void sendMessage(ICommunicationAddress receiver, IFact payload) {
		Action sendAction = retrieveAction(ICommunicationBean.ACTION_SEND);
		JiacMessage message = new JiacMessage(payload);
		invoke(sendAction, new Serializable[] {message, receiver});
		System.out.println("WORKER SENDING " + payload);
	}
}