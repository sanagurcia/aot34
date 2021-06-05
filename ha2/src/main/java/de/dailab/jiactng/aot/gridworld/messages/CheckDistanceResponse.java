package de.dailab.jiactng.aot.gridworld.messages;

import de.dailab.jiactng.agentcore.ontology.IAgentDescription;
import de.dailab.jiactng.aot.gridworld.model.Order;
import de.dailab.jiactng.aot.gridworld.model.Position;
import de.dailab.jiactng.aot.gridworld.model.Worker;

/* worker to broker. replies only if he can reach target in deadline */
public class CheckDistanceResponse extends GameMessage {

    private static final long serialVersionUID = -4218934830297618064L;
    public String orderId;
    public int distance;
    public Result result;

    //Order, worker, workerID,result
    public CheckDistanceResponse(String orderId, int distance, Result result) {
        this.orderId = orderId;
        this.distance = distance;
        this.result = result;
    }
}
