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
import de.dailab.jiactng.aot.gridworld.model.Position;


import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * You can use this stub as a starting point for your broker bean or start from scratch.
 */

public class BrokerBean extends AbstractAgentBean {

	/*
	 * it's probably a good idea to keep track of a few variables here, like
	 * the communication address of the server and your workers, the current game ID,
	 * your active orders, etc.
	 */

	/* List containing all worker agents, including those not activated */
	private List<IAgentDescription> allMyWorkers;
	/* List containing activated worker agents */
	private List<IAgentDescription> myActiveWorkers;
	/* List containing currently contracted worker agents */
	private List<IAgentDescription> myContractedWorkers;

	/* Server (referee) address */
	private ICommunicationAddress serverAddress;

	/* Current game related attributes */
	private Integer gameId;
	private Position gridSize;
	private List<Position> obstacles;

	@Override
	public void doStart() throws Exception {
		super.doStart();
		this.allMyWorkers = new ArrayList<>();
		this.myActiveWorkers = new ArrayList<>();
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

		/* TODO:
			1) Process take order from server
			2) If new order from server, choose not busy worker, assign order
				Send AssignOrder to chosen worker
				2.1) Worker confirm?
					If not, assign to other worker.
		 */

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
	}

	private void handleOrderMessage(OrderMessage msg){
		// TODO
		/* take order? */
	}

	private void handleTakeOrderConfirm(TakeOrderConfirm msg){
		// TODO
		/* Two cases: SUCCESS, FAIL */
		// if success, choose not contracted worker and assign order
			// later: handle AssignOrderConfirm
	}

	private void handleOrderCompleted(OrderCompleted msg) {
		// TODO
		/* Two cases: SUCCESS, FAIL */
		// Update myOrders, myContractedWorkers
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

			/* Add worker to myActiveWorkers List */
			this.myActiveWorkers.add(this.allMyWorkers.get(i));
		}
	}

	private void startNewGame() {
		StartGameMessage startGameMsg = new StartGameMessage();
		startGameMsg.brokerId = thisAgent.getAgentId();
//		startGameMsg.gridFile = "grids/04_01.grid";		// TODO: use working grid file
		startGameMsg.gridFile = null;	// temporary solution
		this.sendMessage(this.serverAddress, startGameMsg);
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
