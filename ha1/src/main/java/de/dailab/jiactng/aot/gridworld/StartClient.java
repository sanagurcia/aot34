package de.dailab.jiactng.aot.gridworld;

import de.dailab.jiactng.agentcore.SimpleAgentNode;

public class StartClient {

	public static void main(String[] args) {
		SimpleAgentNode.startAgentNode("config/server.xml", null);

		System.out.println("Starting client from StartClient.java");
	}
}
