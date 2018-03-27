package net.floodlightcontroller.wireless.master;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentMsgServer implements Runnable{

	protected static Logger log = LoggerFactory.getLogger(AgentMsgServer.class);

	// Wireless Message types
	private final String WIRELESS_MSG_PING = "ping";
	private final String WIRELESS_MSG_PROBE = "probe";
	private final String WIRELESS_MSG_AUTH = "auth";
	private final String WIRELESS_MSG_ASSOC = "station";
	private final String WIRELESS_MSG_DISASSOC = "disassoc";
	private final String WIRELESS_MSG_DEAUTH = "deauth";
	private final String WIRELESS_MSG_PUBLISH = "publish";
	
	private final int ODIN_SERVER_PORT;

	private DatagramSocket controllerSocket;
	private final ExecutorService executor;
	private final WirelessMaster wirelessMaster;

	public AgentMsgServer(WirelessMaster om, int port, ExecutorService executor) {
		this.wirelessMaster = om;
		this.ODIN_SERVER_PORT = port;
		this.executor = executor;
	}

	@Override
	public void run() {

		try {
			controllerSocket = new DatagramSocket(ODIN_SERVER_PORT);
		} catch (IOException e) {
			e.printStackTrace();
		}

		while (true) {
			try {
				final byte[] receiveData = new byte[1024]; 
				final DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
				controllerSocket.receive(receivedPacket);
				executor.execute(new AgentMsgHandler(receivedPacket));
			} catch (IOException e) {
				log.error("controllerSocket.accept() failed: " + ODIN_SERVER_PORT);
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	/** Protocol handlers **/

	private void receivePing(final InetAddress agentAddr) {
		wirelessMaster.receivePing(agentAddr);
	}

	private void receiveProbe(final InetAddress agentAddr, 
			final MacAddress clientHwAddress, final String ssid) {
		wirelessMaster.receiveProbe(agentAddr, clientHwAddress, ssid);
	}
	
	private void receiveAssoc(final InetAddress agentAddr, 
			final MacAddress clientHwAddress, final String staInfo) {
		wirelessMaster.receiveAssoc(agentAddr, clientHwAddress, staInfo);
	}
	
	private void receiveDisassoc(final InetAddress agentAddr, 
			final MacAddress clientHwAddress, final String reason) {
		wirelessMaster.receiveDisassoc(agentAddr, clientHwAddress, reason);
	}

	private class AgentMsgHandler implements Runnable {
		final DatagramPacket receivedPacket;

		public AgentMsgHandler(final DatagramPacket dp) {
			receivedPacket = dp;
		}

		// Agent message handler
		public void run() {
			final String msg = new String(receivedPacket.getData()).trim().toLowerCase();
			final InetAddress agentAddr = receivedPacket.getAddress();
			
			if (msg.equals(WIRELESS_MSG_PING)) {
				receivePing(agentAddr);
				return;
			}
			
			/**
			 * message format:
			 * -----------------------------------------
			 * | type | client mac | payload		   |
			 * -----------------------------------------
			 */
			final String[] fields = msg.split(" ", 3);
			final String msg_type = fields[0];
			final String staAddress = fields[1];
			
			
			switch (msg_type) {
			case WIRELESS_MSG_PROBE:
				String ssid = "";
				if (fields.length > 2) {
					// SSID is specified in the scan
					ssid = fields[2];
				}
				receiveProbe(agentAddr, MacAddress.of(staAddress), ssid);
				break;
			case WIRELESS_MSG_ASSOC:
				if (fields.length > 2) {
					String staInfo = fields[2];
					receiveAssoc(agentAddr, MacAddress.of(staAddress), staInfo);
				}
				break;
			case WIRELESS_MSG_DISASSOC:
			case WIRELESS_MSG_DEAUTH:
				String reason = null;
				if (fields.length > 2) {
					reason = fields[2];
				}
				receiveDisassoc(agentAddr, MacAddress.of(staAddress), reason);
				break;
			}
		}
	}
}
