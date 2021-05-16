package de.dailab.jiactng.aot.gridworld.client;

import de.dailab.jiactng.aot.gridworld.messages.AssignOrder;
import de.dailab.jiactng.aot.gridworld.messages.WorkerConfirm;

import de.dailab.jiactng.agentcore.AbstractAgentBean;
import de.dailab.jiactng.agentcore.comm.message.JiacMessage;
import de.dailab.jiactng.agentcore.knowledge.IFact;
import de.dailab.jiactng.aot.gridworld.messages.WorkerMessage;
import de.dailab.jiactng.aot.gridworld.model.Order;
import de.dailab.jiactng.aot.gridworld.model.Position;
import de.dailab.jiactng.aot.gridworld.model.Worker;
import de.dailab.jiactng.aot.gridworld.messages.ActivateAgent;


public class WorkerBean extends AbstractAgentBean {

	/* TODO:
		state info:
			contracted: boolean
			gameState:
				previousMoveValid: boolean
				atTarget: boolean
				currentMove: Position

		init()
			setup state info
			?notify broker you're up and running

		exec()
			checkNewAssignments:
				if NOT contracted:
					acceptAssignment(
						contracted = true
					)
				else:
					rejectAssignment()

			if contracted:
				updateGameState(){
					if WorkerConfirm(SUCCESS):
						if atTarget:
							log("Order successfully completed")
							contracted = false
							break
						else:
							previousMoveValid = true
					else:
						previousMoveValid = false
				}
				if previousMoveValid: doMove(){
					moveTowardsTarget(
						updatePosition()
						updateAtTarget()
					)
					if atTarget:
						notifyReferee(ORDER)
					else:
						notifyReferee(MOVE)
				}
				else:
					log("previous move invalid")
					give up & notify broker
				}

			else:
				log('idle, waiting for assignment...')
	 */

	private boolean active;		// worker only active if involved in game
	private boolean contracted;		// worker has been assigned an order and currently carrying out
	private boolean previousMoveValid;	// referee (server) approved last move
	private boolean atTarget;	// worker is at order target
	private Worker me;		// class with info about myself, contains Position attribute
	private Order currentOrder;	// == null if not contracted
	// private grid map

	@Override
	public void doStart() throws Exception {
		super.doStart();
		this.active = false;
		log.info("Starting worker agent with id: " + thisAgent.getAgentId() + ", on parent node: " + thisAgent.getAgentNode().getUUID());
	}

	@Override
	public void execute() {

		log.info("in execute() cycle!");

		/* Check inbox */
		for (JiacMessage message : memory.removeAll(new JiacMessage())) {
			Object payload = message.getPayload();

			if (payload instanceof ActivateAgent) {
				ActivateAgent activateAgentMsg = (ActivateAgent) payload;
				// activate(): you're playing!

			}
			else if (payload instanceof AssignOrder) {
				AssignOrder assignOrderMsg = (AssignOrder) payload;
				// do something

			}
			else if (payload instanceof WorkerConfirm) {
				WorkerConfirm workerConfirmMsg = (WorkerConfirm) payload;
				// do something
			}
		}
	}

	/* Helper methods */
	private void activate(Worker worker){
		this.contracted = false;
		this.atTarget = false;
		this.previousMoveValid = true;
		this.me = worker;
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