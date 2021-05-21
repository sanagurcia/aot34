package de.dailab.jiactng.aot.gridworld.messages;

import de.dailab.jiactng.agentcore.ontology.IAgentDescription;
import de.dailab.jiactng.aot.gridworld.model.Order;
import de.dailab.jiactng.aot.gridworld.model.Worker;

/* worker to broker. replies only if he can reach target in deadline */
public class CheckDistance extends GameMessage {

    private static final long serialVersionUID = -4218934830297618064L;

    public Order order;
    public Worker worker;
    public String id;

    public CheckDistance (Order order, Worker worker, String id) {
        this.order = order;
        this.worker = worker;
        this.id = id;
    }
}
