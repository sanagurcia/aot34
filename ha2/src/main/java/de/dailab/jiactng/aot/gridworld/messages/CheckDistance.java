package de.dailab.jiactng.aot.gridworld.messages;

import de.dailab.jiactng.aot.gridworld.model.Order;
import de.dailab.jiactng.aot.gridworld.model.Position;
import de.dailab.jiactng.aot.gridworld.model.Worker;

/* broker to worker */
public class CheckDistance extends GameMessage {

    private static final long serialVersionUID = -4218934830297618064L;

    public String orderId;
    public Position position;
    public int deadline;
    public int turn;


    public CheckDistance( String orderId, Position position, int deadline, int turn) {
        this.orderId = orderId;
        this.position = position;
        this.deadline = deadline;
        this.turn = turn;
    }
}
