package de.dailab.jiactng.aot.gridworld.messages;

import de.dailab.jiactng.aot.gridworld.model.Order;

// From broker to worker: this class can be modified for future functionality!
public class AssignOrder extends GameMessage {

    private static final long serialVersionUID = 90733914536684736L;

    public Order order;

}
