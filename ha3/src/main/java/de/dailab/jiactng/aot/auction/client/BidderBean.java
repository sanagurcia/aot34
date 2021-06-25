package de.dailab.jiactng.aot.auction.client;

import de.dailab.jiactng.agentcore.AbstractAgentBean;
import de.dailab.jiactng.agentcore.action.Action;
import de.dailab.jiactng.agentcore.comm.*;
import de.dailab.jiactng.agentcore.comm.message.JiacMessage;
import de.dailab.jiactng.agentcore.knowledge.IFact;
import de.dailab.jiactng.aot.auction.onto.*;
import org.sercho.masp.space.event.SpaceEvent;
import org.sercho.masp.space.event.SpaceObserver;
import org.sercho.masp.space.event.WriteCallEvent;

import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;

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


	private ICommunicationAddress auctioneerAAddress;
	private ICommunicationAddress auctioneerBAddress;
	private ICommunicationAddress auctioneerCAddress;

	// our bidder id
	private String myId;

	// our unique group token
	private String myGroupToken;

	// strings are set one time only
	private int auctioneerAId;
	private int auctioneerBId;
	private int auctioneerCId;

	private Wallet myWallet;

	Map<Integer, Double> bidOnItems;

	// this is the money we would have, if we would buy everything we want
	private double calculatedMoney;








	@Override
	public void doStart() throws Exception {
		super.doStart();

		//String messageGroup = ICommunicationBean.ACTION_JOIN_GROUP;
		//IGroupAddress groupAddress = CommunicationAddressFactory.createGroupAddress(messageGroup);
		//thisAgent.getCommunication().joinGroup(groupAddress);

		this.bidOnItems = new HashMap<>();
		memory.attach(new BidderBean.MessageObserver(), new JiacMessage());
		log.info("Starting BidderBean.");
	}

	@Override
	public void execute() {

		System.out.println("ICH MACHE ETWAS");

		// because message observer does not seem to work..
		for (JiacMessage message : memory.removeAll(new JiacMessage())) {
			handleMessage(message);
		}
	}

	private void handleMessage(JiacMessage message) {
		Object payload = message.getPayload();

		if (payload instanceof StartAuctions)
		{
			this.handleStartAuctions((StartAuctions) payload, message.getSender());
		}
		else if (payload instanceof InitializeBidder)
		{
			this.handleInitializeBidder((InitializeBidder) payload);
		}
		else if (payload instanceof StartAuction)
		{
			this.handleStartAuction((StartAuction) payload, message.getSender());
		}
		else if (payload instanceof CallForBids)
		{
			this.handleCallForBids((CallForBids) payload);
		}
		else if (payload instanceof InformBuy)
		{
			this.handleInformBuy((InformBuy) payload);
		}
		else if (payload instanceof InformSell)
		{
			this.handleInformSell((InformSell) payload);
		}
		else if (payload instanceof EndAuction)
		{
			this.handleEndAuction((EndAuction) payload);
		}
	}

	/* receive StartAuctions message from AuctioneerMetaBean => reply with Register message to AuctioneerMetaBean */
	private void handleStartAuctions(StartAuctions payload, ICommunicationAddress sender){
		System.out.println(payload.toString());

		// to fix their bug and don't send multiple Register messages for same auction
		if(this.auctionsId == payload.getAuctionsId()) return;

		this.auctionsId = payload.getAuctionsId();

		// send Register message to AuctioneerMetaBean
		sendMessage(sender, new Register(this.myId, this.myGroupToken));
	}

	/* receive InitializeBidder message from AuctioneerMetaBean with wallet */
	private void handleInitializeBidder(InitializeBidder payload){
		System.out.println(payload.toString());

		// make sure this message is for us
		if(!payload.getBidderId().equals(this.myId)) return;

		this.myWallet = payload.getWallet();
		this.calculatedMoney = this.myWallet.getCredits();
	}

	/* receive StartAuction message from each AuctioneerBean (A/B/C) */
	private void handleStartAuction(StartAuction payload, ICommunicationAddress sender){
		System.out.println(payload.toString());

		if(payload.getMode() == StartAuction.Mode.A)
		{
			this.auctioneerAId = payload.getAuctioneerId();
			this.auctioneerAAddress = sender;
		}
		else if(payload.getMode() == StartAuction.Mode.B)
		{
			this.auctioneerBId = payload.getAuctioneerId();
			this.auctioneerBAddress = sender;
		}
		else if(payload.getMode() == StartAuction.Mode.C)
		{
			this.auctioneerCId = payload.getAuctioneerId();
			this.auctioneerCAddress = sender;
		}
		else System.out.println("Unknown StartAuction Mode.");

		// TODO: this message may contain number of items and items to be sold...
	}

	/* Choose Auction Type Handler (A/B/C) on instanceof CallForBids message */
	private void handleCallForBids(CallForBids payload){
		System.out.println(payload.toString());

		if (payload.getMode() == CallForBids.CfBMode.BUY)
		{
			buyCallForBids(payload);
		}
		else if (payload.getMode() == CallForBids.CfBMode.SELL)
		{
			sellCallForBids(payload);
		}
		else System.out.println("Unknown CallForBids Mode.");
	}

	/* React to CallForBids.BUY - only reply if interested */
	private void buyCallForBids(CallForBids payload) {
		SmartGreedy strategy = new SmartGreedy(this.myWallet);
		double ourOffer = strategy.calculateBid(payload);

		// we are not interested
		if(ourOffer == -1) return;

		this.calculatedMoney -= ourOffer;

		// if we would not have any money after buying we are not interested
		if(this.calculatedMoney < 0)
		{
			this.calculatedMoney += ourOffer;
			return;
		}

		// we send the bid to the auctioneer
		Bid ourBid = new Bid(payload.getAuctioneerId(), this.myId, payload.getCallId(), ourOffer);
		if(payload.getAuctioneerId() == this.auctioneerAId) sendMessage(this.auctioneerAAddress, ourBid);
		else if(payload.getAuctioneerId() == this.auctioneerBId) sendMessage(this.auctioneerBAddress, ourBid);
		else if(payload.getAuctioneerId() == this.auctioneerCId) sendMessage(this.auctioneerCAddress, ourBid);

		// this means something went wrong, so act like nothing was done
		else
		{
			this.calculatedMoney += ourOffer;
			return;
		}

		// add this callId to our bid on items, because InformBuy does not tell you how much money you bid
		this.bidOnItems.put(payload.getCallId(), ourOffer);
	}

	/* React to CallForBids.SELL - only reply if interested */
	private void sellCallForBids(CallForBids payload) {
		// TODO: enter real function here
		SmartGreedy strategy = new SmartGreedy(this.myWallet);
		boolean weWantToSell = strategy.calculateBidDummy(payload);

		// we are not interested
		if(!weWantToSell) return;

		// we send the bid to the auctioneer
		Bid ourBid = new Bid(payload.getAuctioneerId(), this.myId, payload.getCallId(), payload.getMinOffer());
		if(payload.getAuctioneerId() == this.auctioneerAId) sendMessage(this.auctioneerAAddress, ourBid);
		else if(payload.getAuctioneerId() == this.auctioneerBId) sendMessage(this.auctioneerBAddress, ourBid);
		else if(payload.getAuctioneerId() == this.auctioneerCId) sendMessage(this.auctioneerCAddress, ourBid);
		else return;

		// remove sold resources from wallet
		this.myWallet.remove(payload.getBundle());
	}

	/* Update state info based on result of Buy (Auction A/C) */
	private void handleInformBuy(InformBuy payload){
		System.out.println(payload.toString());

		// if we did not buy anything
		if(payload.getType() == InformBuy.BuyType.INVALID || payload.getType() == InformBuy.BuyType.LOST) {
			this.calculatedMoney += this.bidOnItems.get(payload.getCallId());
			this.bidOnItems.remove(payload.getCallId());
			return;
		}

		// update the amount of money we have in our wallet
		this.myWallet.updateCredits(payload.getPrice() * -1);

		// add bought bundle to our wallet
		this.myWallet.add(payload.getBundle());

		this.bidOnItems.remove(payload.getCallId());
	}

	/* Update state info based on result of Buy (Auction B/C) */
	private void handleInformSell(InformSell payload){
		System.out.println(payload.toString());

		// if we did not sell anything
		if(payload.getType() == InformSell.SellType.INVALID || payload.getType() == InformSell.SellType.NOT_SOLD) {
			this.myWallet.add(payload.getBundle());
			return;
		}

		// update the amount of money we have in our wallet
		this.myWallet.updateCredits(payload.getPrice());
	}

	/* receive EndAuction message from AuctioneerMetaBean */
	private void handleEndAuction(EndAuction payload){
		System.out.println(payload.toString());
	}

	/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/
	/*++++++++++++++++++++HELPFUL+STUFF++++++++++++++++++++++++++++*/
	/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

	/* Bidder ID Setter for Spring config in bidder.xml */
	public void setBidderId(String id) { this.myId = id; }

	/* Group ID Setter for Spring config in bidder.xml */
	public void setGroupToken(String id) { this.myGroupToken = id; }

	/* Message Group Setter for Spring config in bidder.xml */
	public void setMessageGroup(String id) { this.groupAddress = id; }

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