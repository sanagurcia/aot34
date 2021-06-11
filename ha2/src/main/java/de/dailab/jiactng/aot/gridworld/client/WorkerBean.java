package de.dailab.jiactng.aot.gridworld.client;

import de.dailab.jiactng.agentcore.action.Action;
import de.dailab.jiactng.agentcore.comm.ICommunicationAddress;
import de.dailab.jiactng.agentcore.comm.ICommunicationBean;
import de.dailab.jiactng.agentcore.knowledge.IFact;
import de.dailab.jiactng.agentcore.ontology.AgentDescription;
import de.dailab.jiactng.agentcore.ontology.IAgentDescription;
import de.dailab.jiactng.aot.gridworld.messages.*;

import de.dailab.jiactng.agentcore.AbstractAgentBean;
import de.dailab.jiactng.agentcore.comm.message.JiacMessage;
import de.dailab.jiactng.aot.gridworld.model.Order;
import de.dailab.jiactng.aot.gridworld.model.Position;
import de.dailab.jiactng.aot.gridworld.model.Worker;
import de.dailab.jiactng.aot.gridworld.model.WorkerAction;

import java.io.Serializable;
import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class WorkerBean extends AbstractAgentBean {

	private boolean active;								// worker only active if involved in game
	private boolean previousMoveValid;					// referee (server) approved last move
	private boolean atTarget;							// worker is at order target
	private int myTurn;									// game turn
	private Worker me;									// class with info about myself, contains Position attribute
	private String myId;								// Classes inheriting from Element--e.g., Worker, Order-- have String IDs
	private Integer gameId; 							// current game id
	private Map<Position, Integer> currentOrderPosition;// list of all target order positions with their deadline
	private Position myPosition;						// my current position on grid
	private Position gridSize; 							// the position of "biggest" grid field possible
	private List<Position> obstacles;					// list of all obstacles - received at beginning of game
	private List<Position> myMoves;						// all Moves in correct order to execute
	private Map<Position, List<Position>> orderMoves; 	// Moves to target for each order - used before order is actually assigned from broker. key is target position, first element in list is start position

	@Override
	public void doStart() throws Exception {
		super.doStart();
		this.active = false;
		this.myTurn = 0;
		this.previousMoveValid = true;
		this.atTarget = false;
		this.currentOrderPosition = new HashMap<>();
		this.myMoves = new ArrayList<>();
		this.orderMoves = new HashMap<>();
		log.info("Starting worker agent with id: " + thisAgent.getAgentId());
	}

	@Override
	public void execute() {

		/* Check inbox */
		for (JiacMessage message : memory.removeAll(new JiacMessage())) {
			Object payload = message.getPayload();

			/* activate worker */
			if (payload instanceof ActivateWorker) {
				ActivateWorker tmp = (ActivateWorker) payload;
				this.activate(tmp);
				this.gridSize = tmp.gridSize;
				this.obstacles = tmp.obstacles;
				this.myTurn = tmp.myTurn;
				System.out.println("Worker Agent " + thisAgent.getAgentId() + " activated: ready to accept orders!");
			}
			/* if not activated return and don't do anything */
			else if (!this.active) {
				return;
			}
			/* check distance to order */
			else if (payload instanceof CheckDistance){
				this.handleCheckDistance((CheckDistance) payload);
			}
			/* start doing order */
			else if (payload instanceof AssignOrder ) {
				this.handleAssignOrder((AssignOrder) payload);
			}
			/* check if last move got accepted */
			else if (payload instanceof WorkerConfirm) {
				this.handleWorkerConfirm((WorkerConfirm) payload);
			}
			/* order is completed: fail or success */
			else if (payload instanceof OrderCompleted) {
				this.handleOrderCompleted((OrderCompleted) payload);
			}
		}

		/* If there is an open task, do move */
		if (this.myMoves.size() > 0){
			/* Make sure last move was valid */
			if (previousMoveValid){
				WorkerAction myMove = this.doMove();
				this.sendMoveToRef(myMove);
				System.out.println("----------------------WORKER EXECUTING NEXT MOVE: " + myMove + " ---------------------------");
				System.out.println("----------------------MY POSITION: " + this.myPosition + "----------------------------------"); // ORDER POSITION: " + this.currentOrder.position + "------------");
				System.out.println("----------------------AT TARGET: " + this.atTarget + "--------------------------.-----------");
			}
			else { System.out.println("Previous move not approved."); }
		}

		this.myTurn++;
	}

	/* calculates the distance from one position to another (considering the obstacles) and returns number of moves necessary to get there. */
	private int calculateDistance (Position firstPosition, Position secondPosition) {
		List<Position> moves = new ArrayList<>();

		moves.add(firstPosition);

		// add logic here

		return moves.size();
	}

	/* figures out if order is possible. If yes return success and distance to target, if not return fail */
	public void handleCheckDistance(CheckDistance cd){

		/* default message if worker declines */
		CheckDistanceResponse msg = new CheckDistanceResponse(cd.orderId, -1, Result.FAIL);
		ICommunicationAddress brokerAddress = this.getBrokerAddress();

		int currentDistance = -1;

		/* if target is too far away */
		if(calculateDistance(this.myPosition, cd.position) >= cd.deadline - this.myTurn - 1){
			this.sendMessage(brokerAddress, msg);
			return;
		}

		/* check if worker has an order already, if not calculate distance and respond */
		if(this.currentOrderPosition.size() == 0) {
			currentDistance = calculateDistance(this.myPosition, cd.position);
			msg = new CheckDistanceResponse(cd.orderId, currentDistance, Result.SUCCESS);
			this.sendMessage(brokerAddress, msg);
			return;
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
					msg = new CheckDistanceResponse(cd.orderId, calculateDistance(this.myPosition, cd.position), Result.SUCCESS);
					this.sendMessage(brokerAddress, msg);
					return;
				}
				before = false;
			}
			if(after){
				if(calculateDistance(this.myPosition, positionTarget) + calculateDistance(positionTarget, cd.position) <= cd.deadline - this.myTurn){
					msg = new CheckDistanceResponse(cd.orderId, calculateDistance(this.myPosition, positionTarget) + calculateDistance(positionTarget, cd.position), Result.SUCCESS);
					this.sendMessage(brokerAddress, msg);
					return;
				}
				after = false;
			}
		}

		this.sendMessage(brokerAddress, msg);
		return;
	}

	/* calculates the moves from one position to another (considering the obstacles)and returns this path */
	private List<Position> calculateMoves (Position firstPosition, Position secondPosition) {
		List<Position> moves = new ArrayList<>();
		moves.add(firstPosition);

		// add logic here

		return moves;
	}

	/* figures out if order is possible. If yes return true and set moves in myMoves */
	public boolean handleCheckMoves (AssignOrder ao){

		int currentDistance = -1;

		/* if target is too far away */
		if(calculateDistance(this.myPosition, ao.targetPosition) >= ao.deadline - this.myTurn - 1){
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
					List<Position> newList = new ArrayList<Position>();
					newList.addAll(calculateMoves(this.myPosition, ao.targetPosition));
					newList.addAll(calculateMoves(ao.targetPosition, positionTarget));
					this.myMoves = newList;
					return true;
				}
				before = false;
			}
			if(after){
				if(calculateDistance(this.myPosition, positionTarget) + calculateDistance(positionTarget, ao.targetPosition) <= ao.deadline - this.myTurn){
					List<Position> newList = new ArrayList<Position>();
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
		if(handleCheckMoves(ao))
			msg.state = Result.SUCCESS;
		else
			msg.state = Result.FAIL;

		this.initAcceptedOrder();
		this.sendMessage(brokerAddress, msg);
	}

	/* Do move towards order
	 * Side-effects: update myPosition and atTarget
	 * Returns executed move/order */
	private WorkerAction doMove() {

		WorkerAction nextMove = this.calculateNextMove();

		/* Execute move and update myPosition */
		assert this.myPosition != null;
		this.myPosition = this.myPosition.applyMove(null, nextMove).orElse(null);

		/* update atTarget */
		this.atTarget = false;
		for(int i = 0; i < this.currentOrderPosition.size(); i++){
			if(this.myPosition.equals(this.currentOrderPosition.get(i))) {
				this.atTarget = true;
				nextMove = WorkerAction.ORDER;
			}
		}

		/* if atTarget = true: return Order, else nextMove */
		return nextMove;
	}

	/* Executes next move in myMoves List
	 * Returns NORTH/SOUTH/EAST/WEST */
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

	private void handleWorkerConfirm(WorkerConfirm msg){
		/* Validate previous move */
		this.previousMoveValid = msg.state.equals(Result.SUCCESS);
	}


	private void handleOrderCompleted(OrderCompleted msg) {

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
		this.me = msg.activatedWorker;
		this.myPosition = this.me.position;
		this.gridSize = msg.gridSize;
		this.obstacles = msg.obstacles;
		this.myId = this.me.id;
		this.gameId = msg.gameId;
	}

	/* checks if worker is already at target when assigned */
	private void initAcceptedOrder(){
		/* Default previous move to true, check if already at target */
		this.previousMoveValid = true;
		this.atTarget = false;
		for(int i = 0; i < this.currentOrderPosition.size(); i++){
			if(this.myPosition.equals(this.currentOrderPosition.get(i)))
				this.atTarget = true;
		}
	}

	/* Infrastructure Functions */
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