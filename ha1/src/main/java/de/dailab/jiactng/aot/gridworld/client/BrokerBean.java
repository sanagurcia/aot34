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
	private Integer currentGameId;
	private Position gridSize;
	private List<Position> obstacles;

	@Override
	public void doStart() throws Exception {
		super.doStart();
		log.info("starting broker agent");
	}

	@Override
	public void execute() {
		log.info("running...");

		// update all my workers
		this.allMyWorkers = getMyWorkerAgents(10);
		// update serverAddress
		this.serverAddress = getServerAddress();

		if (this.currentGameId == null){
			this.startNewGame();
		}

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
				StartGameResponse startGameResponseMsg = (StartGameResponse) payload;
				System.out.println("received start game response");
			}
		}
	}

	private void startNewGame() {
		StartGameMessage startGameMsg = new StartGameMessage();
		startGameMsg.brokerId = thisAgent.getAgentId();
//		startGameMsg.gridFile = "grids/04_01.grid";
		startGameMsg.gridFile = null;
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
