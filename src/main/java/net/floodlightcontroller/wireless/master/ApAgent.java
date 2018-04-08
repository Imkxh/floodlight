package net.floodlightcontroller.wireless.master;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitch;

public class ApAgent implements IApAgent{

	protected static Logger log = LoggerFactory.getLogger(WirelessMaster.class);
	
	// WirelessAgent Handler strings
	private static final String WRITE_HANDLER_ADD_VAP = "add_vap";
	private static final String WRITE_HANDLER_SET_VAP = "set_vap";
	private static final String WRITE_HANDLER_REMOVE_VAP = "remove_vap";
	private static final String WRITE_HANDLER_SEND_PROBE_RESPONSE = "send_probe_response";
	private static final String WRITE_HANDLER_SUBSCRIPTIONS = "subscriptions";
	private static final String WRITE_HANDLER_CHANNEL_SWITCH = "channel_switch";
	private static final String READ_HANDLER_LVAP_TABLE = "lvap_table";
	private static final String READ_HANDLER_CLIENT_STATS = "client_stats";
	private static final String READ_HANDLER_DEVICE_INFO = "device_info";

	// Connect to control socket on WirelessAgent
	private Socket odinAgentSocket = null;
	private PrintWriter outBuf;
	private BufferedReader inBuf;
	private IOFSwitch ofSwitch;
	private InetAddress ipAddress;
	private long lastHeard;
	
	private final int ODIN_AGENT_PORT = 6777;
	
	/**
	 * To be called only once, initialises a connection to the WirelessAgent's
	 * control socket. We let the connection persist so as to save on
	 * setup/tear-down messages with every invocation of an agent. This will
	 * also help speedup handoffs.
	 * 
	 * @param host Click based WirelessAgent host
	 * @param port Click based WirelessAgent's control socket port
	 * @return 0 on success, -1 otherwise
	 */
	public int init(InetAddress host) {
		
		/**
		 * FIXME: need to add openflow entry.
		 */
		try {
			odinAgentSocket = new Socket(host.getHostAddress(), ODIN_AGENT_PORT);
			outBuf = new PrintWriter(odinAgentSocket.getOutputStream(), true);
			inBuf = new BufferedReader(new InputStreamReader(odinAgentSocket
					.getInputStream()));
			ipAddress = host;
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return -1;
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}

		return 0;
	}
	
	public InetAddress getIpAddress() {
		return ipAddress;
	}
	
	public long getLastHeard() {
		return lastHeard;
	}

	public void setLastHeard(long t) {
		this.lastHeard = t;
	}
	
	public Set<WirelessClient> getLvapsRemote() {
		return null;
	}
	
	public Set<WirelessClient> getLvapsLocal() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public Map<MacAddress, Map<String, String>> getRxStats() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public IOFSwitch getSwitch() {
		return ofSwitch;
	}

	/**
	 * Set the IOFSwitch entity corresponding to this agent
	 * 
	 * @param sw the IOFSwitch entity for this agent
	 */
	public void setSwitch(IOFSwitch sw) {
		ofSwitch = sw;
	}
	
	public void removeClientLvap(WirelessClient oc) {
		assert(oc != null);
		invokeWriteHandler(WRITE_HANDLER_REMOVE_VAP, oc.getMacAddress().toString());
	}
	
	public void addClientLvap(WirelessClient oc) {
		assert (oc.getLvap() != null);
		
		StringBuilder sb = new StringBuilder();
		sb.append(oc.getMacAddress()).append(" ")
			.append(oc.getIpAddress().getHostAddress()).append(" ")
			.append(oc.getLvap().getBssid().toString());	
		
		for (String ssid: oc.getLvap().getSsids()) {
			sb.append(" ").append(ssid);
		}
		
		invokeWriteHandler(WRITE_HANDLER_ADD_VAP, sb.toString());
	}

	public void updateClientLvap(WirelessClient oc) {
		
	}
	
	public void sendProbeResponse(MacAddress clientHwAddr, 
			MacAddress bssid, Set<String> ssidList) {
		
		StringBuilder sb = new StringBuilder();
		sb.append(clientHwAddr).append(" ").append(bssid);	
		for (String ssid: ssidList) {
			sb.append(" ");
			sb.append(ssid);
		}
		
		invokeWriteHandler(WRITE_HANDLER_SEND_PROBE_RESPONSE, sb.toString());
	}

	public void setSubscriptions(String subscription) {
		assert(subscription != null && !subscription.equals(""));
		invokeWriteHandler(WRITE_HANDLER_SUBSCRIPTIONS, subscription);
	}

	@Override
	public void switchChannel(MacAddress clientHwAddr, String switchMode, 
			String newChannel, String switchCount) {
		String text = clientHwAddr.toString() + " " + switchMode 
				+ " " + newChannel + " " + switchCount;
		invokeWriteHandler(WRITE_HANDLER_CHANNEL_SWITCH, text);
	}

	@Override
	public void addClientStation(WirelessClient oc) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Internal method to invoke a read handler on the WiAgent
	 * 
	 * @param handlerName OdinAgent handler
	 * @return read-handler string
	 */
	private synchronized String invokeReadHandler(String handlerName, String handlerText) {

		outBuf.println("read " + handlerName + " " + handlerText);

		/*
		 * received read data format:
		 * ----------------------------------------
		 * wiagent handlerName data_length data
		 * ----------------------------------------
		 */
		try {
			/**
			 * parse header
			 */
			int blankNum = 0;
			char[] headbuf = new char[64];
			int i = 0;
			while(true) {
				char c = (char) inBuf.read();
				if (c == ' ') {
					blankNum++;
					if (blankNum == 3) break;
				}
				headbuf[i++] = c;
				if (i ==64) {
					log.warn("invokeReadHandler received data exceeds the set length");
					return null;
				}
			}
			String[] head = new String(headbuf).trim().split(" ");
			if (!head[1].equals(handlerName)) return null;
			
			/**
			 * parse data
			 */
			int dataLength = 0;
			try {
				dataLength = Integer.parseInt(head[2]);
			} catch (NumberFormatException e) {
				e.printStackTrace();
				return null;
			}
			char[] databuf = new char[dataLength];
			for (i = 0; i < dataLength; i++) {
				char c = (char) inBuf.read();
				databuf[i] = c;
			}
			String data = new String(databuf);
			return data;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Internal method to invoke a write handler of the OdinAgent
	 * 
	 * @param handlerName OdinAgent write handler name
	 * @param handlerText Write string
	 */
	private synchronized void invokeWriteHandler(String handlerName,
			String handlerText) {
		String str = "write " + handlerName + " " + handlerText;
		outBuf.println(str.toLowerCase());
		
		//88888 test
		System.out.println("******" + "write " + handlerName + " " + handlerText);
	}
}
