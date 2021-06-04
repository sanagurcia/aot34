package de.dailab.jiactng.aot.gridworld.messages;

import de.dailab.jiactng.aot.gridworld.model.Position;
import de.dailab.jiactng.aot.gridworld.model.Worker;

import java.util.List;

public class ActivateWorker extends GameMessage {
    /*
    Message from broker to worker agent, notifying worker that he is playing
    To be sent by broker after startGameResponse received, containing initial workers and game info
     */

    private static final long serialVersionUID = -1767870528936318379L;

    /** the size of the grid for game */
    public Position gridSize;

    /** obstacles (optional) for game */
    public List<Position> obstacles;

    /** one worker from list of workers playing in game (as provided by server) */
    public Worker activatedWorker;
}
