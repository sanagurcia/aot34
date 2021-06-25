package de.dailab.jiactng.aot.auction.client;

import de.dailab.jiactng.agentcore.AbstractAgentBean;
import de.dailab.jiactng.agentcore.action.Action;
import de.dailab.jiactng.agentcore.comm.ICommunicationAddress;
import de.dailab.jiactng.agentcore.comm.ICommunicationBean;
import de.dailab.jiactng.agentcore.comm.message.JiacMessage;
import de.dailab.jiactng.agentcore.knowledge.IFact;
import de.dailab.jiactng.aot.auction.onto.*;
import org.sercho.masp.space.event.SpaceEvent;
import org.sercho.masp.space.event.SpaceObserver;
import org.sercho.masp.space.event.WriteCallEvent;

import java.io.Serializable;

/* START */
// receive startAuctions message from meta bean
// => reply with register to meta bean
// receive initializeBidder message from meta bean with wallet

// receive startAuction message from each auctioneer bean
// => do NOT reply

/* AUCTION A : 100 turns */
// receive callForBids message from auctioneer A (buy)
// => if interested: respond with bid message
// receive InformBuy message from auctioneer A (success or fail)

/* AUCTION B : sync with A but still 40 turns after A is done */
// receive callForBids message from auctioneer B (sell)
// => if interested: respond with bid message
// receive InformSell message from auctioneer B

/* AUCTION C */
// currently not relevant

/* END AUCTION */
// receive endAuction message from meta bean


public class BidderBean extends AbstractAgentBean {

	// multicast message group
	private String groupAddress;

	// the id of the current auction
	private int auctionsId;

	// the address of the AuctioneerMetaBean
	private ICommunicationAddress metaBeanAddress;

	// our bidder id
	private String myId;

	// our unique group token
	private String myGroupToken;

	// strings are set one time only
	private int auctioneerAId;
	private int auctioneerBId;
	private int auctioneerCId;

	@Override
	public void doStart() throws Exception {
		super.doStart();
		this.groupAddress = ICommunicationBean.ACTION_JOIN_GROUP;
		this.auctionsId = -1;
		this.metaBeanAddress = null;
		memory.attach(new BidderBean.MessageObserver(), new JiacMessage());
		log.info("starting bidder agent");
	}

	@Override
	public void execute() {

	}

	private void handleMessage(JiacMessage message) {
		Object payload = message.getPayload();

		if (payload instanceof StartAuctions) {
			this.handleStartAuctions((StartAuctions) payload, message.getSender());
		}
		else if (payload instanceof InitializeBidder) {
			this.handleInitializeBidder((InitializeBidder) payload);
		}
		else if (payload instanceof StartAuction) {
			this.handleStartAuction((StartAuction) payload);
		}
		else if (payload instanceof CallForBids) {
			this.handleCallForBids((CallForBids) payload);
		}
		else if (payload instanceof InformBuy) {
			this.handleInformBuy((InformBuy) payload);
		}
		else if (payload instanceof InformSell) {
			this.handleInformSell((InformSell) payload);
		}

		/* TODO: till here */

		else if (payload instanceof EndAuction) {
			this.handleEndAuction((EndAuction) payload);
		}
	}

	/* receive StartAuctions message from AuctioneerMetaBean
	 * => reply with Register message to AuctioneerMetaBean */
	private void handleStartAuctions(StartAuctions payload, ICommunicationAddress sender){
		System.out.println(payload.toString());

		// to fix their bug and don't send multiple Register messages for same auction
		if(this.auctionsId == payload.getAuctionsId()) return;

		this.auctionsId = payload.getAuctionsId();
		this.metaBeanAddress = sender;

		// send Register message to AuctioneerMetaBean
		Register reg = new Register(this.myId, this.myGroupToken);
		sendMessage(this.metaBeanAddress, reg);
	}

	/* receive InitializeBidder message from AuctioneerMetaBean with wallet */
	private void handleInitializeBidder(InitializeBidder payload){
		// make sure this message is for us
		if(payload.getBidderId() != this.myId) return;

	}

	private void handleStartAuction(StartAuction payload){
		// receive startAuction message from each auctioneer bean
		// => do NOT reply
	}

	/* Choose Auction Type Handler (A/B/C) on instanceof CallForBids messsage */
	private void handleCallForBids(CallForBids payload){
		if (payload.getMode() == CallForBids.CfBMode.BUY){
			buyCallForBids(payload);
		}
		else if (payload.getMode() == CallForBids.CfBMode.SELL){
			sellCallForBids(payload);
		}
		else {
			System.out.println("Unknown CfBMode");
		}
	}

	/* React to CfB.SELL */
	private void sellCallForBids(CallForBids payload) {
	}

	/* React to CfB.BUY */
	private void buyCallForBids(CallForBids payload) {
	}

	/* Update state info based on result of Buy (Auction A/C) */
	private void handleInformBuy(InformBuy payload){
		// update state info
		// receive InformBuy message from auctioneer A (success or fail)
	}

	/* Update state info based on result of Buy (Auction B/C) */
	private void handleInformSell(InformSell payload){
		// update state
		// receive InformSell message from auctioneer B
	}

	private void handleEndAuction(EndAuction payload){
		// receive endAuction message from meta bean
	}

	/* Setter Methods for Spring config in bidder.xml */
	/* Bidder ID Setter */
	public void setBidderId(String id) {
		this.myId = id;
	}

	/* Group ID Setter */
	public void setGroupToken(String id) {
		this.myGroupToken = id;
	}

	/* Message Group Setter */
	public void setMessageGroup(String id) {
		this.groupAddress = id;
	}

	/* send message */
	private void sendMessage(ICommunicationAddress receiver, IFact payload) {
		Action sendAction = retrieveAction(ICommunicationBean.ACTION_SEND);
		JiacMessage message = new JiacMessage(payload);
		invoke(sendAction, new Serializable[] {message, receiver});
		System.out.println("BIDDER SENDING: " + payload);
	}

	/* Message Observer Class */
	private class MessageObserver implements SpaceObserver<IFact> {
		@SuppressWarnings("rawtypes")
		@Override
		public void notify(SpaceEvent<? extends IFact> event) {
			if (event instanceof WriteCallEvent) {
				WriteCallEvent writeEvent = (WriteCallEvent) event;
				if (writeEvent.getObject() instanceof JiacMessage) {
					JiacMessage message = (JiacMessage) writeEvent.getObject();
					if (message.getPayload() instanceof JiacMessage) {
						handleMessage(message);
						memory.remove(message);
					}
				}
			}
		}
	}
}