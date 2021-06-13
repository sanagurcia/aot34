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
import org.sercho.masp.space.event.SpaceEvent;
import org.sercho.masp.space.event.SpaceObserver;
import org.sercho.masp.space.event.WriteCallEvent;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class BrokerBean extends AbstractAgentBean {

	/* List containing all worker agents, including those not activated */
	private List<IAgentDescription> allMyWorkers;

	/* List containing activated worker agents */
	private List<IAgentDescription> myActiveWorkers;

	/* List for available worker agents: agents that are active, but not reserved or contracted */
	private List<IAgentDescription> myAvailableWorkers;

	/* list of all orders in game */
	private List<Order> allOrders;

	/* number of workers we asked for their distance of orderId */
	private Map<String, Integer> countAskedWorkers;

	/* Map of all CheckDistanceResponses of orderId */
	private Map<String, List<CheckDistanceResponse>> answeredCheckDistance;

	/* current game turn, increments each execute() */
	private int myTurn;

	/* Server (referee) address */
	private ICommunicationAddress serverAddress;

	/*my own Address*/
	private ICommunicationAddress brokerAddress;

	/* Current game related attributes */
	private Integer gameId;

	/* Orders I've taken, but not confirmed from initiator */
	private List<String> myTakenOrders;

	/* Orders I've asked workers for distance, but not taken nor confirmed from initiator */
	private List<String> myReservedOrders;

	/* Orders confirmed by initiator and contracted out to worker */
	private List<String> myContractedOrders;

	/*  Key: OrderId, Value: Worker */
	private Map<String, IAgentDescription> workerOrderMap;

	/* the worker who is assigned to the orderId which is key */
	private Map<String, IAgentDescription> tempWorkerOrderMap;

	@Override
	public void doStart() throws Exception {
		super.doStart();
		memory.attach(new BrokerBean.MessageObserver(), new JiacMessage());
		this.myTurn = 1;
		this.myTakenOrders = new ArrayList<>();
		this.myReservedOrders = new ArrayList<>();
		this.myContractedOrders = new ArrayList<>();
		this.allMyWorkers = new ArrayList<>();
		this.myActiveWorkers = new ArrayList<>();
		this.myAvailableWorkers = new ArrayList<>();
		this.allOrders = new ArrayList<>();
		this.workerOrderMap = new HashMap<>();
		this.tempWorkerOrderMap = new HashMap<>();
		this.countAskedWorkers = new HashMap<>();
		this.answeredCheckDistance = new HashMap<>();
		this.brokerAddress = getBrokerAddress();
		log.info("starting broker agent");
	}

	@Override
	public void execute() {
		/* update all my workers */
		this.allMyWorkers = this.getMyWorkerAgents(10);

		// update serverAddress
		if (this.serverAddress == null){
			this.serverAddress = this.getServerAddress();
		}

		if (this.gameId == null){
			this.startNewGame();
		}

		else {
			this.myTurn++;
		}
	}

	/* server sends broker orders: broker asks for distance from all available worker */
	private void handleOrderMessage(OrderMessage msg){

		System.out.println("================BROKER: HANDLING NEW ORDER MESSAGE ====================");

		this.allOrders.add(msg.order);

		CheckDistance cd = new CheckDistance(msg.order.id, msg.order.position,msg.order.deadline, this.myTurn);

		if(this.myAvailableWorkers.isEmpty()) return;
		this.myReservedOrders.add(msg.order.id);

		/* for each available worker: ask for distance */
		for (int i = 0; i < this.myAvailableWorkers.size(); i++){
			this.countAskedWorkers.put(cd.orderId, i+1);
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
		CheckDistanceResponse checkDistanceResponse = new CheckDistanceResponse(orderId,123456789, Result.FAIL, null);
		this.sendMessage(this.brokerAddress,checkDistanceResponse);
	}

	/* Worker only answers if he can reach target in time, then assign this worker */
	public void handleCheckDistanceConfirm(CheckDistanceResponse cd){

		if((this.countAskedWorkers.get(cd.orderId) != this.answeredCheckDistance.get(cd.orderId).size()) && cd.distance != 123456789) return;
		if(this.myTakenOrders.contains(cd.orderId)) return;
		if(this.myContractedOrders.contains(cd.orderId)) return;
		if(this.myAvailableWorkers.isEmpty()) return;
		if(this.answeredCheckDistance.get(cd.orderId) == null) return;

		/* get list of responses */
		List<CheckDistanceResponse> list = new ArrayList<>();
		for (Map.Entry<String, List<CheckDistanceResponse>> response : this.answeredCheckDistance.entrySet()) {
			if (response.getKey().equals(cd.orderId)) {
				list = response.getValue();
			}
		}

		boolean isThereAnyone = false;
		for(CheckDistanceResponse tmp : list){
			if (tmp.result == Result.SUCCESS) {
				isThereAnyone = true;
				break;
			}
		}

		if(!isThereAnyone) {
			return;
		}

		/* construct TakeOrderMessage and send to server */
		TakeOrderMessage takeOrderMsg = new TakeOrderMessage();
		takeOrderMsg.orderId = cd.orderId;
		takeOrderMsg.brokerId = thisAgent.getAgentId();
		takeOrderMsg.gameId = this.gameId;

		/* choose worker who is closest to target */
		int dist = 1000000;
		ICommunicationAddress currentAgent = null;
		CheckDistanceResponse cdr = null;
		for (CheckDistanceResponse checkDistanceResponse : list) {
			if (checkDistanceResponse.distance < dist) {
				cdr = checkDistanceResponse;
				dist = checkDistanceResponse.distance;
				currentAgent = checkDistanceResponse.sender;
			}
		}

		/* get worker who was assigned */
		for(IAgentDescription agent : this.myAvailableWorkers){
			if(agent.getMessageBoxAddress().equals(currentAgent)){
				this.tempWorkerOrderMap.put(cd.orderId, agent);
				list.remove(cdr);
				this.answeredCheckDistance.put(cd.orderId, list);
				break;
			}
		}

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

	}

	/* Msg from Server to Broker, confirming the order */
	private void handleTakeOrderConfirm(TakeOrderConfirm msg){

		/* Assign order to chosen available reserved-worker.*/
		if(!this.tempWorkerOrderMap.containsKey(msg.orderId)){
			return;
		}

		IAgentDescription assignedWorker = this.tempWorkerOrderMap.get(msg.orderId);
		if (msg.state == Result.SUCCESS && findOrder(msg.orderId) != null){

			/* Construct AssignOrder message */
			AssignOrder assignOrderMsg = new AssignOrder();

			/* Look for order in taken orders list */
			for (String myTakenOrder : this.myTakenOrders) {
				if (myTakenOrder.equals(msg.orderId)) {
					assignOrderMsg.orderId = myTakenOrder;
					assignOrderMsg.targetPosition = findOrder(myTakenOrder).position;
					assignOrderMsg.deadline = findOrder(myTakenOrder).deadline;
					assignOrderMsg.turn = this.myTurn;
					break;
				}
			}

			/* Send AssignOrder to assignedWorker */
			this.sendMessage(assignedWorker.getMessageBoxAddress(), assignOrderMsg);

			/* Add order to contracted orders and worker to contracted workers */
			this.myContractedOrders.add(assignOrderMsg.orderId);
			this.myTakenOrders.remove(assignOrderMsg.orderId);
		}

		/* If order confirm fail, free up one reserved worker */
		else {
			this.myAvailableWorkers.add(assignedWorker);
			String deleteOrder = "";
			for (String myTakenOrder : this.myTakenOrders) {
				if (myTakenOrder.equals(msg.orderId)) {
					deleteOrder = myTakenOrder;
				}
			}
			this.myTakenOrders.remove(deleteOrder);
		}
	}

	private Order findOrder(String orderId){
		for (Order allOrder : allOrders) {
			if (allOrder.id.equals(orderId))
				return allOrder;
		}
		return null;
	}

	/* Worker only answers if he can reach target in time, then assign this worker */
	public int handleNewWorker(CheckDistanceResponse cd){

		if(!this.myTakenOrders.contains(cd.orderId)) return -1;
		if(!this.myContractedOrders.contains(cd.orderId)) return -1;
		if(this.myAvailableWorkers.isEmpty()) return -1;
		if(this.answeredCheckDistance.get(cd.orderId) == null) return -1;

		/* get all answers from workers in List */
		List<CheckDistanceResponse> tmpList = new ArrayList<>();
		for (Map.Entry<String, List<CheckDistanceResponse>> answers : this.answeredCheckDistance.entrySet()) {
			if (answers.getKey().equals(cd.orderId)) {
				tmpList = answers.getValue();
			}
		}

		/* choose worker who is closest to target */
		int dist = 10000000;
		ICommunicationAddress currentAgent = null;
		CheckDistanceResponse cdr = null;
		for (CheckDistanceResponse checkDistanceResponse : tmpList) {
			if (checkDistanceResponse.distance < dist) {
				cdr = checkDistanceResponse;
				dist = checkDistanceResponse.distance;
				currentAgent = checkDistanceResponse.sender;
			}
		}

		/* get new worker who was assigned */
		for(IAgentDescription agent : this.myAvailableWorkers){
			if(agent.getMessageBoxAddress().equals(currentAgent)){
				this.tempWorkerOrderMap.put(cd.orderId, agent);
				tmpList.remove(cdr);
				this.answeredCheckDistance.put(cd.orderId, tmpList);
			}
		}

		return 1;
	}


	private int getNewWorkerToDoTheWork(String orderId){
		CheckDistanceResponse cd = new CheckDistanceResponse(orderId, -2, Result.FAIL, null);
		return handleNewWorker(cd);
	}

	/* Msg from Worker to Broker, accepting assignment of order */
	/* Move confirming worker from reserved to contracted list */
	private void handleAssignOrderConfirm(AssignOrderConfirm msg, ICommunicationAddress sender){

		IAgentDescription assignedWorker = null;

		/* Look for AssignedWorker in reserved workers list */
		for (IAgentDescription myContractedWorker : this.myAvailableWorkers) {
			if (myContractedWorker.getMessageBoxAddress().equals(sender)) {
				assignedWorker = myContractedWorker;
			}
		}

		String theOrder = "";

		/* Look for order in contracted orders list */
		for (String myContractedOrder : this.myContractedOrders) {
			if (myContractedOrder.equals(msg.orderId)) {
				theOrder = myContractedOrder;
			}
		}

		if (msg.state == Result.FAIL) {
			if(getNewWorkerToDoTheWork(msg.orderId) == -1) {
				this.myContractedOrders.remove(theOrder);
				System.out.println("ORDER COULD NOT BE ASSIGNED TO NEW FREE WORKER");
			}
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
		for (String myContractedOrder : this.myContractedOrders) {
			if (myContractedOrder.equals(msg.orderId)) {
				theOrder = myContractedOrder;
			}
		}

		this.myContractedOrders.remove(theOrder);

		/* Find contracted worker: make him available again */
		if (!theOrder.equals("")){
			IAgentDescription theWorker = this.workerOrderMap.remove(theOrder);
			System.out.println("=================BROKER: ORDER COMPLETED. NOTIFYING WORKER.=====================");

			/* Forward message to worker */
			this.sendMessage(theWorker.getMessageBoxAddress(), msg);
		}
	}

	private void handleEndGameMessage(EndGameMessage msg){
		System.out.println("END GAME. BROKER " + msg.brokerId + " REWARD: " + msg.totalReward);

	}

	private void handleStartGameResponse(StartGameResponse response) {
		/* Set up local variables for game */
		this.gameId = response.gameId;
		Position gridSize = response.size;
		List<Position> obstacles = response.obstacles;

		/* For each initialWorker activate worker from allMyWorkers. Check that enough agent threads for initial workers */
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
			activateWorkerMsg.gridSize = gridSize;
			activateWorkerMsg.obstacles = obstacles;
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
		startGameMsg.gridFile = "/grids/grid_2.grid";
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
		else if (payload instanceof CheckDistanceResponse){
			tmpCD = (CheckDistanceResponse) payload;
			tmpCD.sender = message.getSender();
			if(this.answeredCheckDistance.containsKey(tmpCD.orderId)) {
				value = this.answeredCheckDistance.get(tmpCD.orderId);
			}
			value.add(tmpCD);
			this.answeredCheckDistance.put(tmpCD.orderId, value);
			this.handleCheckDistanceConfirm((CheckDistanceResponse) payload);
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
