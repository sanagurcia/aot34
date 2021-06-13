package de.dailab.jiactng.aot.gridworld.messages;

import de.dailab.jiactng.agentcore.comm.ICommunicationAddress;

/* worker to broker. replies only if he can reach target in deadline */
public class CheckDistanceResponse extends GameMessage {

    private static final long serialVersionUID = -4218934830297618064L;
    public String orderId;
    public int distance;
    public ICommunicationAddress sender;
    public Result result;

    //Order, worker, workerID,result
    public CheckDistanceResponse(String orderId, int distance, Result result, ICommunicationAddress sender) {
        this.orderId = orderId;
        this.distance = distance;
        this.sender = sender;
        this.result = result;
    }
}
