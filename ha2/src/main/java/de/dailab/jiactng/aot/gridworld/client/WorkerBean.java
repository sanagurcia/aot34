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
import de.dailab.jiactng.aot.gridworld.server.ServerBean;
import org.sercho.masp.space.event.SpaceEvent;
import org.sercho.masp.space.event.SpaceObserver;
import org.sercho.masp.space.event.WriteCallEvent;

import java.io.Serializable;
import java.util.List;


public class WorkerBean extends AbstractAgentBean {

	private boolean active;		// worker only active if involved in game
	private boolean contracted;		// worker has been assigned an order and currently carrying out
	private boolean previousMoveValid;	// referee (server) approved last move
	private boolean atTarget;	// worker is at order target
	private Worker me;		// class with info about myself, contains Position attribute
	private String myId;	// Classes inheriting from Element--e.g., Worker, Order-- have String IDs
	private Integer gameId;
	private Order currentOrder;	// == null if not contracted
	private Position myPosition;	// my current position on grid
	private Position gridSize;
	public List<Position> obstacles;

	@Override
	public void doStart() throws Exception {
		super.doStart();
		this.active = false;
		memory.attach(new WorkerBean.MessageObserver(), new JiacMessage());
		log.info("Starting worker agent with id: " + thisAgent.getAgentId());
	}

	@Override
	public void execute() {

		/* If currently contracted with task, do move */
		if (contracted){
			/* Make sure last move was valid and not already at target */
			if (previousMoveValid){
				if (!atTarget) {
					WorkerAction myMove = this.doMove();
					this.sendMoveToRef(myMove);
					System.out.println("----------------------WORKER EXECUTING NEXT MOVE: " + myMove + " ---------------------------");
					System.out.println("----------------------MY POSITION: " + this.myPosition + "; ORDER POSITION: " + this.currentOrder.position + "------------");
					System.out.println("----------------------AT TARGET: " + this.atTarget + "-------------------------------------");
				}
				else {
					/* If at already at target, send ORDER to ref */
					this.sendMoveToRef(WorkerAction.ORDER);
					System.out.println("----------------------WORKER AT TARGET!!!-----------------------");
				}
			}
			else { System.out.println("Previous move not approved."); }
		}
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

	/* Do move towards order
	*  Side-effects: update myPosition, atTarget
	*  Returns executed move/order */
	private WorkerAction doMove() {
		WorkerAction nextMove = this.calculateNextMove();
		/* Execute move and update myPosition */
		assert this.myPosition != null;
		this.myPosition = this.myPosition.applyMove(null, nextMove).orElse(null);
		/* update atTarget */
		this.atTarget = this.myPosition.equals(this.currentOrder.position);
		/* if atTarget return Order, else nextMove */
		return nextMove;
	}

	/* Encapsulate next move calculation logic here
	*  Returns NORTH/SOUTH/EAST/WEST
	* */
	private WorkerAction calculateNextMove() {

		int targetX = currentOrder.position.x;
		int targetY = currentOrder.position.y;
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

	private void sendAssignOrderConfirm(Result state, String orderId) {
		AssignOrderConfirm msg = new AssignOrderConfirm();
		msg.orderId = orderId;
		msg.workerId = this.myId;
		msg.state = state;
		ICommunicationAddress brokerAddress = this.getBrokerAddress();
		this.sendMessage(brokerAddress, msg);
	}

	private void initAcceptedOrder(){
		/* Default previous move to true, check if already at target */
		this.previousMoveValid = true;
		this.atTarget = this.myPosition == this.currentOrder.position;
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
		this.contracted = false;
		this.me = msg.activatedWorker;
		this.myPosition = this.me.position;
		this.gridSize = msg.gridSize;
		this.obstacles = msg.obstacles;
		this.myId = this.me.id;
		this.gameId = msg.gameId;
	}


	/* Message Handlers */
	private void handleAssignOrder(AssignOrder msg) {
		/* If not currently contracted, accept assignment */
		if (!contracted){
			this.contracted = true;
			this.currentOrder = msg.order;
			this.initAcceptedOrder();
			this.sendAssignOrderConfirm(Result.SUCCESS, msg.order.id);
		}
		else {
			this.sendAssignOrderConfirm(Result.FAIL, msg.order.id);
		}
	}

	public void handleCheckDistance(CheckDistance cd){
		if(this.myPosition.distance(cd.order.position) >= cd.order.deadline - 1) return;
		CheckDistance msg = new CheckDistance(cd.order, this.me, this.myId);
		ICommunicationAddress brokerAddress = this.getBrokerAddress();
		this.sendMessage(brokerAddress, msg);
	}

	private void handleWorkerConfirm(WorkerConfirm msg){
		/* Validate previous move */
		this.previousMoveValid = msg.state.equals(Result.SUCCESS);
	}

	private void handleOrderCompleted(OrderCompleted msg) {
		this.contracted = false;
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