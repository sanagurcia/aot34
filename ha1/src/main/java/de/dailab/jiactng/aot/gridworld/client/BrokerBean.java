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
import de.dailab.jiactng.aot.gridworld.model.Order;
import de.dailab.jiactng.aot.gridworld.model.Position;


import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * You can use this stub as a starting point for your broker bean or start from scratch.
 */

public class BrokerBean extends AbstractAgentBean {

	/* TODO: associate worker agents with model/Worker.java class */
	/* List containing all worker agents, including those not activated */
	private List<IAgentDescription> allMyWorkers;
	/* List containing activated worker agents */
	private List<IAgentDescription> myActiveWorkers;
	/* List for available worker agents: agents that are active, but not reserved or contracted */
	private List<IAgentDescription> myAvailableWorkers;
	/* List containing worker agents reserved for future assignment */
	private List<IAgentDescription> myReservedWorkers;
	/* List for currently contracted worker agents: agents busy with orders */
	private List<IAgentDescription> myContractedWorkers;

	/* Server (referee) address */
	private ICommunicationAddress serverAddress;

	/* Current game related attributes */
	private Integer gameId;
	private Position gridSize;
	private List<Position> obstacles;

	/* TODO: use Maps for getting elements by id, instead of lists!
	*  TODO: associate order with contracted/reserved worker */
	/* Orders I've taken, but not confirmed from initiator */
	private List<Order> myTakenOrders;
	/* Orders confirmed by initiator and contracted out to worker */
	private List<Order> myContractedOrders;

	@Override
	public void doStart() throws Exception {
		super.doStart();
		this.myTakenOrders = new ArrayList<>();
		this.myContractedOrders = new ArrayList<>();
		this.allMyWorkers = new ArrayList<>();
		this.myActiveWorkers = new ArrayList<>();
		this.myAvailableWorkers = new ArrayList<>();
		this.myReservedWorkers = new ArrayList<>();
		this.myContractedWorkers = new ArrayList<>();
		log.info("starting broker agent");
	}

	@Override
	public void execute() {
		log.info("running...");

		// update all my workers
		this.allMyWorkers = this.getMyWorkerAgents(10);
		// update serverAddress
		this.serverAddress = this.getServerAddress();

		/* Handle incoming messages without listener */
		for (JiacMessage message : memory.removeAll(new JiacMessage())) {
			Object payload = message.getPayload();

			if (payload instanceof StartGameResponse) {
				this.handleStartGameResponse((StartGameResponse) payload);
			}
			else if (payload instanceof OrderMessage){
				this.handleOrderMessage((OrderMessage) payload);
			}
			else if (payload instanceof TakeOrderConfirm){
				this.handleTakeOrderConfirm((TakeOrderConfirm) payload);
			}
			else if (payload instanceof OrderCompleted){
				this.handleOrderCompleted((OrderCompleted) payload);
			}
			else if (payload instanceof EndGameMessage){
				this.handleEndGameMessage((EndGameMessage) payload);
			}
		}

		if (this.gameId == null){
			this.startNewGame();
		}

		/* Log state of worker agents */
		System.out.println("Total worker agents: " + this.allMyWorkers.size());
		System.out.println("Active worker agents: " + this.myActiveWorkers.size());
		System.out.println("Available worker agents: " + this.myAvailableWorkers.size());
		System.out.println("Contracted worker agents: " + this.myContractedWorkers.size());
	}

	private void handleOrderMessage(OrderMessage msg){

		System.out.println("================BROKER: HANDLING NEW ORDER MESSAGE ====================");

		/* Check if available worker exists */
		if (this.myAvailableWorkers.size() > 0){

			/* construct TakeOrderMessage and send to server */
			TakeOrderMessage takeOrderMsg = new TakeOrderMessage();
			takeOrderMsg.orderId = msg.order.id;
			takeOrderMsg.brokerId = thisAgent.getAgentId();
			takeOrderMsg.gameId = this.gameId;
			this.sendMessage(this.serverAddress, takeOrderMsg);

			/* Add order to myTakenOrders. Can be retrieved later based on id */
			this.myTakenOrders.add(msg.order);

			/* Choose and reserve worker agent for assignment */
			IAgentDescription chosenWorker = this.choseAvailableWorker(msg.order);
			this.myAvailableWorkers.remove(chosenWorker);
			this.myReservedWorkers.add(chosenWorker);
		}
	}

	private void handleTakeOrderConfirm(TakeOrderConfirm msg){

		/* Assign order to first available reserved-worker.
		 * 	Can be improved by associating reserved-workers with particular orders beforehand */
		IAgentDescription assignedWorker = this.myReservedWorkers.get(0);
		this.myReservedWorkers.remove(assignedWorker);

		if (msg.state == Result.SUCCESS){

			/* Construct AssignOrder message */
			AssignOrder assignOrderMsg = new AssignOrder();
			/* Find order in myTakenOrders by id */
			Order theOrder = this.myTakenOrders.stream().filter(o -> o.id.equals(msg.orderId)).findFirst().orElse(null);
			assignOrderMsg.order = theOrder;

			/* Send AssignOrder to assignedWorker */
			this.sendMessage(assignedWorker.getMessageBoxAddress(), assignOrderMsg);

			System.out.println("The order POSITION: " + theOrder.position);

			/* Add order to contracted orders */
			this.myContractedOrders.add(theOrder);

			/* Immediately adding assignedWorker to contractedWorkers, without confirmation from him */
			/* TODO: deal here and elsewhere with AssignOrderConfirm */
			this.myContractedWorkers.add(assignedWorker);
		}
	}

	private void handleOrderCompleted(OrderCompleted msg) {
		/* Two cases: SUCCESS, FAIL */
		Order theOrder = this.myTakenOrders.stream().filter(o -> o.id.equals(msg.orderId)).findFirst().orElse(null);
		this.myContractedOrders.remove(theOrder);
		// TODO: locate contracted-worker based on order-id and make worker available again.
	}

	private void handleEndGameMessage(EndGameMessage msg){
		System.out.println("End game. Broker " + msg.brokerId + " reward: " + msg.totalReward);
	}


	private void handleStartGameResponse(StartGameResponse response) {
		/* Set up local variables for game */
		this.gameId = response.gameId;
		this.gridSize = response.size;
		this.obstacles = response.obstacles;

		/* For each initialWorker activate worker from allMyWorkers */
		for (int i=0; i<response.initialWorkers.size(); i++){
			/* Create ActivateWorker message */
			ActivateWorker activateWorkerMsg = new ActivateWorker();
			activateWorkerMsg.gameId = this.gameId;
			activateWorkerMsg.gridSize = this.gridSize;
			activateWorkerMsg.obstacles = this.obstacles;
			activateWorkerMsg.activatedWorker = response.initialWorkers.get(i);

			/* TODO: make sure allMyAgents.size >= initialWorkers.size */
			/* Get WorkerAgent[i] address and send ActivateWorker msg */
			ICommunicationAddress workerAddress = this.allMyWorkers.get(i).getMessageBoxAddress();
			this.sendMessage(workerAddress, activateWorkerMsg);

			/* Add new worker to myActiveWorkers and myAvailableWorkers List */
			this.myActiveWorkers.add(this.allMyWorkers.get(i));
			this.myAvailableWorkers.add(this.allMyWorkers.get(i));
		}
	}

	private void startNewGame() {
		StartGameMessage startGameMsg = new StartGameMessage();
		startGameMsg.brokerId = thisAgent.getAgentId();
//		startGameMsg.gridFile = "grids/04_01.grid";		// TODO: use working grid file
		startGameMsg.gridFile = null;	// temporary solution
		this.sendMessage(this.serverAddress, startGameMsg);
	}

	private IAgentDescription choseAvailableWorker(Order order){
		/* Choose first agent available: Can be improved! */
		return this.myAvailableWorkers.get(0);
	}

	/* Use example function to get server address, return null if not found */
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

	/** example function for using getAgentNode() and retrieving a list of all worker agents */
	private List<IAgentDescription> getMyWorkerAgents(int maxNum) {
		String nodeId = thisAgent.getAgentNode().getUUID();
		return thisAgent.searchAllAgents(new AgentDescription(null, null, null, null, null, nodeId)).stream()
				.filter(a -> a.getName().startsWith("WorkerAgent"))
				.limit(maxNum)
				.collect(Collectors.toList());
	}

	/** example function to send messages to other agents */
	private void sendMessage(ICommunicationAddress receiver, IFact payload) {
		Action sendAction = retrieveAction(ICommunicationBean.ACTION_SEND);
		JiacMessage message = new JiacMessage(payload);
		invoke(sendAction, new Serializable[] {message, receiver});
		System.out.println("BROKER SENDING " + payload);
	}
}
