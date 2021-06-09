package de.dailab.jiactng.aot.gridworld;

import de.dailab.jiactng.agentcore.SimpleAgentNode;

public class StartServer {

	public static void main(String[] args) {
		System.out.println("Running startAgentNode() with config/server.xml from StartServer.java");
		SimpleAgentNode.startAgentNode("config/server.xml", null);
	}
}
