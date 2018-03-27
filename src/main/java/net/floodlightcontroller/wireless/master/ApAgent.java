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

	protected static Logger log = LoggerFactory.getLogger(ApAgent.class);
	
	// WirelessAgent Handler strings
	private static final String WRITE_HANDLER_ADD_VAP = "add_vap";
	private static final String WRITE_HANDLER_SET_VAP = "set_vap";
	private static final String WRITE_HANDLER_REMOVE_VAP = "remove_vap";
	private static final String WRITE_HANDLER_SEND_PROBE_RESPONSE = "send_probe_response";

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
		log.info("-------------------------------------agent init");
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
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
		
	}
	
	public void addClientLvap(WirelessClient oc) {
		assert (oc.getLvap() != null);
		
		String ssidList = "";
		
		for (String ssid: oc.getLvap().getSsids()) {
			ssidList += " " + ssid;
		}
		
		invokeWriteHandler(WRITE_HANDLER_ADD_VAP, oc.getMacAddress().toString(),
				oc.getIpAddress().getHostAddress() + " " + oc.getLvap().getBssid().toString() 
				+ ssidList + " 0");
	}

	public void updateClientLvap(WirelessClient oc) {
		
	}
	
	public void sendProbeResponse(MacAddress clientHwAddr, 
			MacAddress bssid, Set<String> ssidList) {
		
		StringBuilder sb = new StringBuilder();
		sb.append(bssid);		
		for (String ssid: ssidList) {
			sb.append(" ");
			sb.append(ssid);
		}
		
		invokeWriteHandler(WRITE_HANDLER_SEND_PROBE_RESPONSE, 
				clientHwAddr.toString(), sb.toString());
	}

	public void setSubscriptions(String subscriptionList) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateRssiFilterExpress(String express) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchChannel(MacAddress clientHwAddr, String switchMode, String newChannel, String switchCount) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addClientStation(WirelessClient oc) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getStaAggregationRx(WirelessClient client) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setStaAggregationRx(WirelessClient client, String agg_rx) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Internal method to invoke a read handler on the WiAgent
	 * 
	 * @param handlerName OdinAgent handler
	 * @return read-handler string
	 */
	private synchronized String invokeReadHandler(String handlerName, 
			String clientHwAddr, String handlerText) {

		outBuf.println("read " + handlerName + " " + clientHwAddr + " " + handlerText);
		log.info("read " + handlerName + " " + clientHwAddr + " " + handlerText);
		try {
			String headLine = null;
			headLine = inBuf.readLine();
			
			int numBytes = Integer.parseInt(headLine.split(" ")[2]);
			String data = "";
			if (numBytes > 0) {
				char[] buf = new char[numBytes];
				inBuf.read(buf, 0, numBytes);
				data = data + new String(buf);
			}
			return data;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	
	/**
	 * Internal method to invoke a write handler of the OdinAgent
	 * 
	 * @param handlerName OdinAgent write handler name
	 * @param handlerText Write string
	 */
	private synchronized void invokeWriteHandler(String handlerName,
			String clientHwAddr, String handlerText) {
		outBuf.println("write " + handlerName + " " + clientHwAddr + " " + handlerText);
		log.info("write " + handlerName + " " + clientHwAddr + " " + handlerText);
	}
}
