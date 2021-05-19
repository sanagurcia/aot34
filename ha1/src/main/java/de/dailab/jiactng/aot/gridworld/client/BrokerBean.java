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

/*
* TODO: smart data structures
*  1) Tuple list that pairs taken/contracted order with reserved/contracted worker,
*  2) Associate IAgentDescription with model/Worker.java class,
*  3) Map structures for myOrders & myWorkers, with IDs as keys,
*  4) Refactor accordingly.
* */


public class BrokerBean extends AbstractAgentBean {

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

	/* Orders I've taken, but not confirmed from initiator */
	private List<Order> myTakenOrders;
	/* Orders confirmed by initiator and contracted out to worker */
	private List<Order> myContractedOrders;

	/* Temporary solution for associating Workers with orders
	*  Key: OrderId, Value: AgentDescription */
	private Map<String, IAgentDescription> workerOrderMap;


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
		this.workerOrderMap = new HashMap<>();
		log.info("starting broker agent");
	}

	@Override
	public void execute() {
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
			else if (payload instanceof AssignOrderConfirm){
				this.handleAssignOrderConfirm((AssignOrderConfirm) payload, message.getSender());
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
		System.out.println("BROKER--Total worker agents: " + this.allMyWorkers.size());
		System.out.println("BROKER--Active worker agents: " + this.myActiveWorkers.size());
		System.out.println("BROKER--Available worker agents: " + this.myAvailableWorkers.size());
		System.out.println("BROKER--Contracted worker agents: " + this.myContractedWorkers.size());
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
			IAgentDescription chosenWorker = this.chooseAvailableWorker(msg.order);
			this.myAvailableWorkers.remove(chosenWorker);
			this.myReservedWorkers.add(chosenWorker);
		}
	}

	/* Msg from Server to Broker, confirming the order */
	private void handleTakeOrderConfirm(TakeOrderConfirm msg){

		/* Assign order to first available reserved-worker.
		 * 	Can be improved by associating reserved-workers with particular orders beforehand */
		IAgentDescription assignedWorker = this.myReservedWorkers.get(0);

		if (msg.state == Result.SUCCESS){

			/* Construct AssignOrder message */
			AssignOrder assignOrderMsg = new AssignOrder();
			/* Find order in myTakenOrders by id */
			Order theOrder = this.myTakenOrders.stream().filter(o -> o.id.equals(msg.orderId)).findFirst().orElse(null);
			assignOrderMsg.order = theOrder;

			/* Send AssignOrder to assignedWorker */
			this.sendMessage(assignedWorker.getMessageBoxAddress(), assignOrderMsg);

			/* Add order to contracted orders */
			this.myContractedOrders.add(theOrder);
		}

		/* If order confirm fail, free up one reserved worker */
		else {
			this.myReservedWorkers.remove(assignedWorker);
		}
	}

	/* Msg from Worker to Broker, accepting assignment of order */
	/* Move confirming worker from reserved to contracted list */
	private void handleAssignOrderConfirm(AssignOrderConfirm msg, ICommunicationAddress sender){

		/* Look for AssignedWorker in reserved workers list */
		IAgentDescription assignedWorker = this.myReservedWorkers.stream()
				.filter(agent -> agent.getMessageBoxAddress().equals(sender)).findFirst().orElse(null);

		Order theOrder = this.myTakenOrders.stream().filter(o -> o.id.equals(msg.orderId)).findFirst().orElse(null);

		if (assignedWorker != null){
			/* Remove AssignedWorker from reserved list */
			this.myReservedWorkers.remove(assignedWorker);

			/* If assignment confirmed, and order still valid, add worker to contracted list*/
			if (msg.state.equals(Result.SUCCESS) && this.myContractedOrders.contains(theOrder)){

				this.myContractedWorkers.add(assignedWorker);

				/* Associate worker (AgentDescription) with OrderID */
				this.workerOrderMap.put(msg.orderId, assignedWorker);
			}
		}
	}

	/* Msg from Server to Broker, stating if order succeeded or failed */
	private void handleOrderCompleted(OrderCompleted msg) {
		Order theOrder = this.myContractedOrders.stream().filter(o -> o.id.equals(msg.orderId)).findFirst().orElse(null);
		this.myContractedOrders.remove(theOrder);

		/* Find contracted worker: make him available again */
		if (theOrder != null){
			IAgentDescription theWorker = this.workerOrderMap.remove(theOrder.id);
			this.myContractedWorkers.remove(theWorker);
			this.myAvailableWorkers.add(theWorker);
			System.out.println("=================BROKER: ORDER COMPLETED. MAKING WORKER AVAILABLE.==============");
			System.out.println("=================BROKER: NOTIFYING WORKER.======================================");

			/* Forward message to worker */
			this.sendMessage(theWorker.getMessageBoxAddress(), msg);
		}
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

	private IAgentDescription chooseAvailableWorker(Order order){
		/* Choose first agent available: Can be improved! */
		return this.myAvailableWorkers.get(0);
	}


	/* INFRASTRUCTURE FUNCTIONS */

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
