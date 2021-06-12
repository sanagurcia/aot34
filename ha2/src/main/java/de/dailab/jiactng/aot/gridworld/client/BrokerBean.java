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
import de.dailab.jiactng.aot.gridworld.model.Broker;
import de.dailab.jiactng.aot.gridworld.model.Order;
import de.dailab.jiactng.aot.gridworld.model.Position;
import org.sercho.masp.space.event.SpaceEvent;
import org.sercho.masp.space.event.SpaceObserver;
import org.sercho.masp.space.event.WriteCallEvent;

import java.time.LocalDateTime;

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

	private List<Order> allOrders;

	private Map<String, Integer> countAskedWorkers;
	private Map<String, List<CheckDistanceResponse>> answeredCheckDistance;

	private int myTurn;

	/* Server (referee) address */
	private ICommunicationAddress serverAddress;

	/*my own Address*/
	private ICommunicationAddress brokerAddress;


	/* Current game related attributes */
	private Integer gameId;
	private Position gridSize;
	private List<Position> obstacles;

	/* Orders I've taken, but not confirmed from initiator */
	private List<String> myTakenOrders;
	/* Orders I've asked workers for distance, but not taken nor confirmed from initiator */
	private List<String> myReservedOrders;
	/* Orders confirmed by initiator and contracted out to worker */
	private List<String> myContractedOrders;
	/* Orders that timed out after checking distance, auction has to start */
	private List<String> myTimedOutOrders;

	/* Temporary solution for associating Workers with orders
	*  Key: OrderId, Value: AgentDescription */
	private Map<String, IAgentDescription> workerOrderMap;
	private Map<String, IAgentDescription> tempWorkerOrderMap;

	@Override
	public void doStart() throws Exception {
		super.doStart();
		memory.attach(new BrokerBean.MessageObserver(), new JiacMessage());
		this.myTurn = 0;
		this.myTakenOrders = new ArrayList<>();
		this.myReservedOrders = new ArrayList<>();
		this.myContractedOrders = new ArrayList<>();
		this.allMyWorkers = new ArrayList<>();
		this.myActiveWorkers = new ArrayList<>();
		this.myAvailableWorkers = new ArrayList<>();
		this.myReservedWorkers = new ArrayList<>();
		this.myContractedWorkers = new ArrayList<>();
		this.allOrders = new ArrayList<>();
		this.workerOrderMap = new HashMap<>();
		this.tempWorkerOrderMap = new HashMap<>();
		this.countAskedWorkers = new HashMap<>();
		this.answeredCheckDistance = new HashMap<>();
		log.info("starting broker agent");
		this.brokerAddress = getBrokerAddress();
	}

	@Override
	public void execute() {
		// update all my workers
		this.allMyWorkers = this.getMyWorkerAgents(10);

		// update serverAddress
		if (this.serverAddress == null){
			this.serverAddress = this.getServerAddress();
		}

		if (this.gameId == null){
			this.startNewGame();
		}

		/* Log state of worker agents */
		// System.out.println("BROKER--Total worker agents: " + this.allMyWorkers.size());
		// System.out.println("BROKER--Active worker agents: " + this.myActiveWorkers.size());
		// System.out.println("BROKER--Available worker agents: " + this.myAvailableWorkers.size());
		// System.out.println("BROKER--Contracted worker agents: " + this.myContractedWorkers.size());

		this.myTurn++;
	}

	/* server sends broker orders: broker asks for distance from all available worker */
	private void handleOrderMessage(OrderMessage msg){

		System.out.println("================BROKER: HANDLING NEW ORDER MESSAGE ====================");

		this.allOrders.add(msg.order);

		CheckDistance cd = new CheckDistance(msg.order.id, msg.order.position,msg.order.deadline);

		if(this.myAvailableWorkers.isEmpty() || this.myAvailableWorkers.size() == 0) return;
		this.myReservedOrders.add(msg.order.id);

		/* for each available worker: ask for distance */
		for (int i = 0; i < this.myAvailableWorkers.size(); i++){
			this.countAskedWorkers.put(cd.orderId, i);
			this.sendMessage(this.myAvailableWorkers.get(i).getMessageBoxAddress(), cd);
		}

		/* start a timer for the reserved order */
		new java.util.Timer().schedule(
				new java.util.TimerTask() {
					@Override
					public void run() {
						sendTimerMessage(cd.orderId);
					}
				},
				500
		);

	}

	public void sendTimerMessage(String orderId){
		//possibly not needed
		//TimerMessage timerMsg = new TimerMessage(orderId);
		//this.sendMessage(this.brokerAddress, timerMsg);
		CheckDistanceResponse checkDistanceResponse = new CheckDistanceResponse(orderId,-2,Result.FAIL);
		this.sendMessage(this.brokerAddress,checkDistanceResponse);
	}

	public void handleTimerMessage (TimerMessage msg){
		//checks if the Timeout already exist or adds it to the myTimedOutOrders && checks if the order is in MyReservedOrder
		//possibly not needed
		if(!this.myTimedOutOrders.contains(msg.orderId) && this.myReservedOrders.contains(msg.orderId)) {
			myTimedOutOrders.add(msg.orderId);
		}
	}

	/* Worker only answers if he can reach target in time, then assign this worker */
	public void handleCheckDistanceConfirm(CheckDistanceResponse cd, ICommunicationAddress sender){

		if((this.countAskedWorkers.get(cd.orderId) != this.answeredCheckDistance.get(cd.orderId).size()) && cd.distance !=-2) return;
		if(this.myTakenOrders.indexOf(cd.orderId) != -1) return;
		if(this.myContractedOrders.indexOf(cd.orderId) != -1) return;
		if(this.myAvailableWorkers.isEmpty() || this.myAvailableWorkers.size() == 0) return;

		/* construct TakeOrderMessage and send to server */
		TakeOrderMessage takeOrderMsg = new TakeOrderMessage();
		takeOrderMsg.orderId = cd.orderId;
		takeOrderMsg.brokerId = thisAgent.getAgentId();
		takeOrderMsg.gameId = this.gameId;

		int j = 0;
		int sizeList = this.myReservedOrders.size();
		while(sizeList > 0) {
			String tempOrderId = this.myReservedOrders.get(j);
			if (tempOrderId.equals(cd.orderId)) {
				this.myTakenOrders.add(tempOrderId);
				this.myReservedOrders.remove(j);
				sizeList--;
				this.sendMessage(this.serverAddress, takeOrderMsg);
			}
			sizeList--;
			j++;
		}

		int i = 0;
		sizeList = this.myAvailableWorkers.size();
		IAgentDescription currAgent;
		while(sizeList > 0) {
			currAgent = this.myAvailableWorkers.get(i);  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// hier nur die aus der answered attribut durchgehen
			if (currAgent.getMessageBoxAddress().equals(sender)) {
				/* reserve worker agent for assignment */
				this.myAvailableWorkers.remove(currAgent);
				this.myReservedWorkers.add(currAgent);
				this.tempWorkerOrderMap.put(cd.orderId, currAgent); ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// in diese Liste nur den Worker packen, der am besten geeignet ist
				break;
			}
			sizeList--;
			i++;
		}
	}

	/* Msg from Server to Broker, confirming the order */
	private void handleTakeOrderConfirm(TakeOrderConfirm msg){

		/* Assign order to chosen available reserved-worker.*/
		if(this.myReservedWorkers.isEmpty()) return;
		if(!this.tempWorkerOrderMap.containsKey(msg.orderId)) return;

		IAgentDescription assignedWorker = this.tempWorkerOrderMap.get(msg.orderId);
		this.tempWorkerOrderMap.remove(msg.orderId);
		this.myReservedWorkers.remove(assignedWorker);

		if (msg.state == Result.SUCCESS && findOrder(msg.orderId) != null){

			/* Construct AssignOrder message */
			AssignOrder assignOrderMsg = new AssignOrder();

			/* Look for order in taken orders list */
			for(int i = 0; i < this.myTakenOrders.size(); i++){
				if(this.myTakenOrders.get(i).equals(msg.orderId)){
					assignOrderMsg.orderId = this.myTakenOrders.get(i);
					assignOrderMsg.targetPosition = findOrder(this.myTakenOrders.get(i)).position;
					assignOrderMsg.deadline = findOrder(this.myTakenOrders.get(i)).deadline;
				}
			}

			/* Send AssignOrder to assignedWorker */
			this.sendMessage(assignedWorker.getMessageBoxAddress(), assignOrderMsg);

			/* Add order to contracted orders and worker to contracted workers */
			this.myContractedOrders.add(assignOrderMsg.orderId);
			this.myTakenOrders.remove(assignOrderMsg.orderId);
			this.myContractedWorkers.add(assignedWorker);
		}

		/* If order confirm fail, free up one reserved worker */
		else {
			this.myAvailableWorkers.add(assignedWorker);
			String deleteOrder = "";
			for(int i = 0; i < this.myTakenOrders.size(); i++){
				if(this.myTakenOrders.get(i).equals(msg.orderId)){
					deleteOrder = this.myTakenOrders.get(i);
				}
			}
			this.myTakenOrders.remove(deleteOrder);
		}
	}

	private Order findOrder(String orderId){
		for(int i = 0; i < allOrders.size(); i++){
			if(allOrders.get(i).id.equals(orderId))
				return allOrders.get(i);
		}
		return null;
	}

	/* Msg from Worker to Broker, accepting assignment of order */
	/* Move confirming worker from reserved to contracted list */
	private void handleAssignOrderConfirm(AssignOrderConfirm msg, ICommunicationAddress sender){

		IAgentDescription assignedWorker = null;

		/* Look for AssignedWorker in reserved workers list */
		for(int i = 0; i < this.myContractedWorkers.size(); i++){
			if(this.myContractedWorkers.get(i).getMessageBoxAddress().equals(sender)){
				assignedWorker = this.myContractedWorkers.get(i);
			}
		}

		String theOrder = "";

		/* Look for order in contracted orders list */
		for(int i = 0; i < this.myContractedOrders.size(); i++){
			if(this.myContractedOrders.get(i).equals(msg.orderId)){
				theOrder = this.myContractedOrders.get(i);
			}
		}

		if (msg.state == Result.FAIL) {
			this.myAvailableWorkers.add(assignedWorker);
			this.myContractedWorkers.remove(assignedWorker);
			this.myContractedOrders.remove(theOrder);  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// hier einem anderen worker die aufgabe geben, aus answered attribut
		}

		else if (msg.state == Result.SUCCESS){
			/* Associate worker (AgentDescription) with OrderID */
			this.workerOrderMap.put(msg.orderId, assignedWorker);
		}
	}

	/* Msg from Server to Broker, stating if order succeeded or failed */
	private void handleOrderCompleted(OrderCompleted msg) {

		String theOrder = "";
		for(Order o : this.allOrders){
			if(o.id.equals(msg.orderId))
				msg.pos = o.position;
		}

		/* Look for order in contracted orders list */
		for(int i = 0; i < this.myContractedOrders.size(); i++){
			if(this.myContractedOrders.get(i).equals(msg.orderId)){
				theOrder = this.myContractedOrders.get(i);
			}
		}

		this.myContractedOrders.remove(theOrder);

		/* Find contracted worker: make him available again */
		if (theOrder != ""){
			IAgentDescription theWorker = this.workerOrderMap.remove(theOrder);
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
		/* Check that enough agent threads for initial workers */
		int inactiveWorkers = this.allMyWorkers.size();
		for (int i=0; i<response.initialWorkers.size(); i++){
			if (inactiveWorkers == 0){
				System.out.println("==============BROKER: WARNING! NOT ENOUGH WORKER THREADS TO HANDLE SO MANY INITIAL WORKERS==========");
				System.out.println("=========================CHECK CLIENT.XML AND OTHER SETTINGS FILES==================================");
				break;
			}

			/* Create ActivateWorker message */
			ActivateWorker activateWorkerMsg = new ActivateWorker();
			activateWorkerMsg.gameId = this.gameId;
			activateWorkerMsg.gridSize = this.gridSize;
			activateWorkerMsg.obstacles = this.obstacles;
			activateWorkerMsg.myTurn = this.myTurn;
			activateWorkerMsg.activatedWorker = response.initialWorkers.get(i);

			/* Get WorkerAgent[i] address and send ActivateWorker msg */
			ICommunicationAddress workerAddress = this.allMyWorkers.get(i).getMessageBoxAddress();
			this.sendMessage(workerAddress, activateWorkerMsg);

			/* Add new worker to myActiveWorkers and myAvailableWorkers List */
			this.myActiveWorkers.add(this.allMyWorkers.get(i));
			this.myAvailableWorkers.add(this.allMyWorkers.get(i));

			/* Decrement number of inactive worker threads*/
			inactiveWorkers = inactiveWorkers - 1;
		}
	}

	private void startNewGame() {
		StartGameMessage startGameMsg = new StartGameMessage();
		startGameMsg.brokerId = thisAgent.getAgentId();
		startGameMsg.gridFile = "/grids/grid_1.grid";
		if (this.serverAddress != null){
			this.sendMessage(this.serverAddress, startGameMsg);
		}
		else {
			System.out.println("BROKER: WAITING FOR SERVER ADDRESS TO START NEW GAME!");
		}
	}

	private void handleMessage(JiacMessage message) {
		Object payload = message.getPayload();

		CheckDistanceResponse tmpCD;
		List<CheckDistanceResponse> value = new ArrayList<>();

		if (payload instanceof StartGameResponse) {
			this.handleStartGameResponse((StartGameResponse) payload);
		}
		else if (payload instanceof OrderMessage){
			this.handleOrderMessage((OrderMessage) payload);
		}
		else if (payload instanceof TakeOrderConfirm){
			this.handleTakeOrderConfirm((TakeOrderConfirm) payload);
		}
		else if (payload instanceof TimerMessage){
			this.handleTimerMessage((TimerMessage) payload);
		}
		else if (payload instanceof CheckDistanceResponse){
			tmpCD = (CheckDistanceResponse) payload;
			if(this.answeredCheckDistance.containsKey(tmpCD.orderId)) {
				value = this.answeredCheckDistance.get(tmpCD.orderId);
			}
			value.add(tmpCD);
			this.answeredCheckDistance.put(tmpCD.orderId, value);
			this.handleCheckDistanceConfirm((CheckDistanceResponse) payload, message.getSender());
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

	/* INFRASTRUCTURE FUNCTIONS */
	/* Message Observer Class */
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

	/* Use example function to get broker address, return null if not found */
	private ICommunicationAddress getBrokerAddress() {
		ICommunicationAddress broker = null;
		IAgentDescription brokerAgent = thisAgent.searchAgent(new AgentDescription(null, "BrokerAgent", null, null, null, null));
		if (brokerAgent != null) {
			broker = brokerAgent.getMessageBoxAddress();
		} else {
			System.out.println("SELF BROKER NOT FOUND!");
		}
		return broker;
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
		// Sending current time to assert that message handling occurs outside of execute() cycle.
		System.out.println("BROKER SENDING @ " + LocalDateTime.now() + "\n\tPayload: " + payload);
	}
}
