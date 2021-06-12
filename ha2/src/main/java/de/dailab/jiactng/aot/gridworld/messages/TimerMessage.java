package de.dailab.jiactng.aot.gridworld.messages;

// From broker to broker: this class can be modified for future functionality!
public class TimerMessage extends GameMessage {

    private static final long serialVersionUID = 90712914970181745L;

    public String orderId;

    public TimerMessage(String orderId){
        this.orderId = orderId;
    }
}
