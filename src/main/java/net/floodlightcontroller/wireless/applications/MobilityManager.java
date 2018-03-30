package net.floodlightcontroller.wireless.applications;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.wireless.master.NotificationCallback;
import net.floodlightcontroller.wireless.master.NotificationCallbackContext;
import net.floodlightcontroller.wireless.master.WirelessApplication;
import net.floodlightcontroller.wireless.master.WirelessClient;
import net.floodlightcontroller.wireless.master.WirelessEventSubscription;
import net.floodlightcontroller.wireless.master.WirelessEventSubscription.EventType;
import net.floodlightcontroller.wireless.master.WirelessEventSubscription.SubType;

public class MobilityManager extends WirelessApplication{
	
	protected static Logger log = LoggerFactory.getLogger(MobilityManager.class);	
	private ConcurrentMap<MacAddress, MobilityStats> clientMap = 
			new ConcurrentHashMap<MacAddress, MobilityStats> ();
	private final long HYSTERESIS_THRESHOLD; // milliseconds
	private final long IDLE_CLIENT_THRESHOLD; // milliseconds
	private final long SIGNAL_STRENGTH_THRESHOLD; // dbm

	public MobilityManager () {
		this.HYSTERESIS_THRESHOLD = 3000;
		this.IDLE_CLIENT_THRESHOLD = 4000;
		this.SIGNAL_STRENGTH_THRESHOLD = 10;
	}
	
	// Used for testing
	public MobilityManager (long hysteresisThresh, long idleClientThresh, long signalStrengthThresh) {
		this.HYSTERESIS_THRESHOLD = hysteresisThresh;
		this.IDLE_CLIENT_THRESHOLD = idleClientThresh;
		this.SIGNAL_STRENGTH_THRESHOLD = signalStrengthThresh;
	}
	
	/**
	 * Register subscriptions
	 */
	private void init () {
		
		WirelessEventSubscription signal_sub = new WirelessEventSubscription(
				MobilityManager.class.getName(), SubType.APAGENT, EventType.MOBILITY_SIGNAL);		
		NotificationCallback signal_cb = new NotificationCallback() {

			@Override
			public void handle(EventType type, String msg) {
				processSignal(msg);
			}

		};
		registerSubscription(signal_sub, signal_cb);
		
		WirelessEventSubscription client_sub = new WirelessEventSubscription(
				MobilityManager.class.getName(), SubType.APPLICATION, EventType.NEW_CLIENT); 
		NotificationCallback client_cb = new NotificationCallback() {

			@Override
			public void handle(EventType type, String msg) {
				updateSignalSubscription(msg);
			}

		};
		registerSubscription(client_sub, client_cb);
		
	}
	
	@Override
	public void run() {
		init (); 
		
		// Purely reactive, so end.
	}
	
	/**
	 * This handler will handoff a client in the event of its
	 * agent having failed.
	 * 
	 * @param oes
	 * @param cntx
	 */
	private synchronized void processSignal(String msg) {
		
		String[] fields = msg.split(" ");
		if (fields.length != 3) {
			log.info("mobility signal processing: the number of parameters is wrong");
			return;
		}
		
		InetAddress agentAddr;
		try {
			agentAddr = InetAddress.getByAddress(fields[0].getBytes());
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return;
		}
		
		MacAddress clientHwAddr = MacAddress.of(fields[1]);
		WirelessClient client = getClientFromHwAddress(clientHwAddr);
		if (client == null)
			return;
		
		int value = Integer.parseInt(fields[2]);
		
		long currentTimestamp = System.currentTimeMillis();
		
		// Assign mobility stats object if not already done
		// add an entry in the clientMap table for this client MAC
		// put the statistics in the table: value of the parameter, timestamp, timestamp
		if (!clientMap.containsKey(clientHwAddr)) {
			clientMap.put(clientHwAddr, new MobilityStats(value, currentTimestamp, currentTimestamp));
		}
		
		MobilityStats stats = clientMap.get(clientHwAddr);
		// Check for out-of-range client
		// a client has sent nothing during a certain time
		if ((currentTimestamp - stats.lastHeard) > IDLE_CLIENT_THRESHOLD) {
			//if(client.getLvap().getAgent().getIpAddress() == cntx.agent.getIpAddress())
	
			//log.info("Mobility manager: out of range client: handing off client " + cntx.clientHwAddress
			//		+ " to agent " + cntx.agent.getIpAddress() + " at " + System.currentTimeMillis());
			//handle longer threshold?
			log.info("Mobility manager: client with MAC address "
						+ clientHwAddr + " was idle longer than " + IDLE_CLIENT_THRESHOLD/1000 
						+ " sec -> Reassociating it to agent " + agentAddr);
			handoffClientToAp(clientHwAddr, agentAddr);
			updateStatsWithReassignment (stats, value, currentTimestamp);
			return;
		}
		
		// If this notification is from the agent that's hosting the client's LVAP. update MobilityStats.
		// Else, check if we should do a handoff.
		if (client.getLvap().getAgent().getIpAddress().equals(agentAddr.getHostAddress())) {
			stats.signalStrength = value;
			stats.lastHeard = currentTimestamp;
		}
		else {
			// Don't bother if we're not within hysteresis period
			if (currentTimestamp - stats.assignmentTimestamp < HYSTERESIS_THRESHOLD)
				return;

			// We're outside the hysteresis period, so compare signal strengths for a handoff
			// I check if the strength is higher (THRESHOLD) than the last measurement stored the
			// last time in the other AP
			if (value >= stats.signalStrength + SIGNAL_STRENGTH_THRESHOLD) {
				log.info("Mobility manager: comparing signal strengths: " + value + ">= " 
						+ stats.signalStrength + " + " + SIGNAL_STRENGTH_THRESHOLD 
						+ " :" + "handing off client " + clientHwAddr
						+ " to agent " + agentAddr + " at " + System.currentTimeMillis());
				handoffClientToAp(clientHwAddr, agentAddr);
				updateStatsWithReassignment (stats, value, currentTimestamp);
				return;
			}
		}
		
	}
	
	private void updateSignalSubscription(String msg) {
		unregisterSubscription(MobilityManager.class.getName(), EventType.MOBILITY_SIGNAL);
		
		Set<WirelessClient> clients = getClients();
		String clientList = "";
		for (WirelessClient wc : clients) {
			clientList += " " + wc.getMacAddress();
		}
		WirelessEventSubscription signal_sub = new WirelessEventSubscription(
				MobilityManager.class.getName(), SubType.APAGENT, EventType.MOBILITY_SIGNAL);
		signal_sub.setExtra(clientList);
		NotificationCallback signal_cb = new NotificationCallback() {

			@Override
			public void handle(EventType type, String msg) {
				processSignal(msg);
			}

		};
		registerSubscription(signal_sub, signal_cb);
	}
	
	private void updateStatsWithReassignment (MobilityStats stats, long signalValue, long now) {
		stats.signalStrength = signalValue;
		stats.lastHeard = now;
		stats.assignmentTimestamp = now;
	}
	
	
	private class MobilityStats {
		public long signalStrength;
		public long lastHeard;
		public long assignmentTimestamp;
		
		public MobilityStats (long signalStrength, long lastHeard, long assignmentTimestamp) {
			this.signalStrength = signalStrength;
			this.lastHeard = lastHeard;
			this.assignmentTimestamp = assignmentTimestamp;
		}
	}

}
