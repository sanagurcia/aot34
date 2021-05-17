package de.dailab.jiactng.aot.gridworld.messages;


// Confirm message from Worker to Broker
public class AssignOrderConfirm extends GameMessage {

    private static final long serialVersionUID = -6463209686315653195L;

    public String orderId;

    public String workerId;

    public Result state;

}
