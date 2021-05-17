package de.dailab.jiactng.aot.gridworld.client;

import de.dailab.jiactng.aot.gridworld.messages.AssignOrder;
import de.dailab.jiactng.aot.gridworld.messages.WorkerConfirm;

import de.dailab.jiactng.agentcore.AbstractAgentBean;
import de.dailab.jiactng.agentcore.comm.message.JiacMessage;
import de.dailab.jiactng.aot.gridworld.model.Order;
import de.dailab.jiactng.aot.gridworld.model.Position;
import de.dailab.jiactng.aot.gridworld.model.Worker;
import de.dailab.jiactng.aot.gridworld.messages.ActivateWorker;
import de.dailab.jiactng.aot.gridworld.model.WorkerAction;

import java.util.List;
import java.util.Optional;
import java.util.Random;


public class WorkerBean extends AbstractAgentBean {

	/* Worker Structure:
		exec()
			if AssignOrder message:
				if NOT contracted:
					acceptAssignment() {
						contracted = true
						send AssignOrderConfirm to Broker
					}
				else:
					rejectAssignment()

			if WorkerConfirm message:
				update previousMoveValid

			if contracted:
				if previousMoveValid:
					if NOT atTarget:
						doMove(){
							calculateNewMove()
							updatePosition()
							updateAtTarget()
						}
						if atTarget:
							notifyReferee(ORDER)
						else:
							notifyReferee(MOVE)
				else:
					log("previous move invalid")

			else:
				chill and be quiet.
	 */

	private boolean active;		// worker only active if involved in game
	private boolean contracted;		// worker has been assigned an order and currently carrying out
	private boolean previousMoveValid;	// referee (server) approved last move
	private boolean atTarget;	// worker is at order target
	private Worker me;		// class with info about myself, contains Position attribute
	private Order currentOrder;	// == null if not contracted
	private Position myPosition;	// my current position on grid
	private Position gridSize;
	public List<Position> obstacles;

	@Override
	public void doStart() throws Exception {
		super.doStart();
		this.active = false;
		log.info("Starting worker agent with id: " + thisAgent.getAgentId());
	}

	@Override
	public void execute() {
		/* Check inbox */
		for (JiacMessage message : memory.removeAll(new JiacMessage())) {
			Object payload = message.getPayload();

			if (payload instanceof ActivateWorker) {
				this.activate((ActivateWorker) payload);
				System.out.println("Worker Agent " + thisAgent.getAgentId() + " activated: ready to accept orders!");
			}
			else if (payload instanceof AssignOrder) {
				this.handleAssignOrder((AssignOrder) payload);
			}
			else if (payload instanceof WorkerConfirm) {
				WorkerConfirm workerConfirmMsg = (WorkerConfirm) payload;
				// do something
			}
		}

		/* If currently contracted with task, do move */
		if (contracted){
			/* Make sure last move was valid and not already at target */
			if (previousMoveValid){
				if (!atTarget) {
					WorkerAction myMove = this.doMove();
					this.sendMoveToRef(myMove);
				}
				else { System.out.println("Already at target."); }
			}
			else { System.out.println("Previous move not approved."); }
		}

	}

	private void sendMoveToRef(WorkerAction myMove) {
		System.out.println("----------------------WORKER EXECUTING NEXT MOVE: " + myMove + " ---------------------------");
	}

	/* Do move towards order
	*  Side-effects: update myPosition, atTarget
	*  Returns executed move/order */
	private WorkerAction doMove() {
		WorkerAction nextMove = this.calculateNextMove();
		/* Execute move and update myPosition */
		assert this.myPosition != null;
		this.myPosition = this.myPosition.applyMove(null, nextMove).orElse(null);
		/* update atTarget */
		this.atTarget = this.myPosition == this.currentOrder.position;
		/* if atTarget return Order, else nextMove */
		return atTarget ? WorkerAction.ORDER : nextMove;
	}

	/* Encapsulate next move calculation logic here
	* Returns NORTH/SOUTH/EAST/WEST
	* */
	private WorkerAction calculateNextMove() {

		/* For now, generate random move and check that it's valid */
		WorkerAction nextMove = WorkerAction.NORTH;
		boolean validMove = false;

		Random rand = new Random();

		/* Generate new random move while invalid */
		while (!validMove) {
			int i = rand.nextInt(4);
			switch (i) {
				case 0:
					nextMove = WorkerAction.SOUTH;
					break;
				case 1:
					nextMove = WorkerAction.WEST;
					break;
				case 2:
					nextMove = WorkerAction.EAST;
					break;
				default:
					break;
			}
			/* check if random move valid*/
			validMove = this.myPosition.applyMove(this.gridSize, nextMove).isPresent();
		}
		return nextMove;
	}

	private void handleAssignOrder(AssignOrder msg) {
		/* If not currently contracted, accept assignment */
		if (!contracted){
			this.contracted = true;
			this.currentOrder = msg.order;
			this.initAcceptedOrder();
			/* TODO: send assign order confirm*/
		}
		else {
			/* TODO: reject assignment */
		}
	}

	/* Default previous move to true, check if already at target */
	private void initAcceptedOrder(){
		this.previousMoveValid = true;
		this.atTarget = this.myPosition == this.currentOrder.position;
	}

	/* Helper methods */
	private void activate(ActivateWorker msg){
		this.active = true;
		this.contracted = false;
		this.me = msg.activatedWorker;
		this.myPosition = this.me.position;
		this.gridSize = msg.gridSize;
		this.obstacles = msg.obstacles;
	}
}



/* NOTES from skeleton:

class WorkerBean
 * this bean represents one of your Worker agents (i.e. each Worker Agent you initialize with this bean
 * will have a separate copy); it's structure will be similar to your Broker agent's
 *
 *
 * note that the number of workers may vary from grid to grid, but the number of worker
 * agents will always be the same (specified in the client.xml); you will have to have your Broker somehow tell
 * the worker agents which of them are currently needed and who may idle
 *
 * you could, theoretically, also control all your Workers from a single worker agent (and
 * bean), or even implement both the Broker and the Worker in the same bean, but that would
 * of course defeat the purpose of this exercise and may not be possible in "real life"

 void doStart()
 * this will be called once when the agent starts and can be used for initialization work
 * note that when this method is executed, (a) it is not guaranteed that all the other
 * agents are already started and/or their actions are known, and (b) the agent's execution
 * has not yet started, so do not wait for any actions to be completed in this method (you
 * can invoke actions, though, if they are already known to the agent)
 *
 *
 * You can use a SpaceObserver to listen to messages, but you can also check messages in execute()
 * and only temporarily attach a SpaceObserver for specific purposes.

 */