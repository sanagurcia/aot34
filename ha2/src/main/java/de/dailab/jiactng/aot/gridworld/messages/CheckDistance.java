package de.dailab.jiactng.aot.gridworld.messages;

import de.dailab.jiactng.aot.gridworld.model.Order;
import de.dailab.jiactng.aot.gridworld.model.Position;
import de.dailab.jiactng.aot.gridworld.model.Worker;

/* worker to broker. replies only if he can reach target in deadline */
public class CheckDistance extends GameMessage {

    private static final long serialVersionUID = -4218934830297618064L;

    public String orderId;
    public Position position;
    public int deadline;


    public CheckDistance( String orderId, Position position, int deadline) {
        this.orderId = orderId;
        this.position = position;
        this.deadline = deadline;
    }
}
