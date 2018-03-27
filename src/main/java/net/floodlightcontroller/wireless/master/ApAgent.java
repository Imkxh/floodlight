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
	
	// Connect to control socket on OdinAgent
	private Socket odinAgentSocket = null;
	private PrintWriter outBuf;
	private BufferedReader inBuf;
	private IOFSwitch ofSwitch;
	private InetAddress ipAddress;
	private long lastHeard;
	
	private final int ODIN_AGENT_PORT = 6777;
		
	public InetAddress getIpAddress() {
		// TODO Auto-generated method stub
		return null;
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

	/**
	 * To be called only once, initialises a connection to the OdinAgent's
	 * control socket. We let the connection persist so as to save on
	 * setup/tear-down messages with every invocation of an agent. This will
	 * also help speedup handoffs.
	 * 
	 * @param host Click based OdinAgent host
	 * @param port Click based OdinAgent's control socket port
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
	
	public IOFSwitch getSwitch() {
		// TODO Auto-generated method stub
		return null;
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
		// TODO Auto-generated method stub
		
	}

	public void updateClientLvap(WirelessClient oc) {
		// TODO Auto-generated method stub
		
	}
	
	public void sendProbeResponse(MacAddress clientHwAddr, MacAddress bssid, Set<String> ssidLists) {
		// TODO Auto-generated method stub
		
	}

	public long getLastHeard() {
		// TODO Auto-generated method stub
		return 0;
	}

	public void setLastHeard(long t) {
		this.lastHeard = t;
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

}
