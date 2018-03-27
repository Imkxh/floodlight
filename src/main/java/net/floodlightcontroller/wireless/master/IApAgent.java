package net.floodlightcontroller.wireless.master;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.types.MacAddress;

import net.floodlightcontroller.core.IOFSwitch;

public interface IApAgent {

	/**
	 * Probably need a better identifier
	 * @return the agent's IP address
	 */
	public InetAddress getIpAddress ();
	
	
	/**
	 * Get a list of VAPs that the agent is hosting
	 * @return a list of wirelessClient entities on the agent
	 */
	public Set<WirelessClient> getLvapsRemote ();
	
	
	/**
	 * Return a list of LVAPs that the master knows this
	 * agent is hosting. Between the time an agent has
	 * crashed and the master detecting the crash, this
	 * can return stale values.
	 * 
	 * @return a list of wirelessClient entities on the agent
	 */
	public Set<WirelessClient> getLvapsLocal ();
	
	
	/**
	 * Retrive Rx-stats from the OdinAgent.
	 * 
	 *  @return A map of stations' MAC addresses to a map
	 *  of properties and values.
	 */
	public Map<MacAddress, Map<String, String>> getRxStats ();
	
	
	/**
	 * To be called only once, intialises a connection to the OdinAgent's
	 * control socket. We let the connection persist so as to save on
	 * setup/tear-down messages with every invocation of an agent. This
	 * will also help speedup handoffs. This process can be ignored
	 * in a mock agent implementation
	 * 
	 * @param host Click based OdinAgent host
	 * @return 0 on success, -1 otherwise
	 */
	public int init (InetAddress host);
	
	
	/**
	 * Get the IOFSwitch for this agent
	 * @return ofSwitch
	 */
	public IOFSwitch getSwitch ();
	
	
	/**
	 * Set the IOFSwitch entity corresponding to this agent
	 * 
	 * @param sw the IOFSwitch entity for this agent
	 */
	public void setSwitch (IOFSwitch sw);
	
	public void updateRssiFilterExpress(String express);
	
	
	/**
	 * Remove an LVAP from the AP corresponding to this agent
	 * 
	 * @param staHwAddr The STA's ethernet address
	 */
	public void removeClientLvap (WirelessClient oc);
	
		
	/**
	 * Add an LVAP to the AP corresponding to this agent
	 */
	public void addClientLvap (WirelessClient oc);
	
	/**
	 * Let the station switch channel, and transfer LVAP of this station
	 * @param clientHwAddr
	 * @param switchMode
	 * @param newChannel
	 * @param switchCount
	 */
	public void switchChannel(MacAddress clientHwAddr,
			String switchMode, String newChannel, String switchCount);
	
	/**
	 * Add an station to the AP corresponding to this agent
	 * @param oc
	 */
	public void addClientStation (WirelessClient oc);
	
	
	/**
	 * Update a virtual access point with possibly new IP, BSSID, or SSID
	 * 
	 * @param staHwAddr The STA's ethernet address
	 * @param staIpAddr The STA's IP address
	 * @param vapBssid The STA specific BSSID
	 * @param staEssid The STA specific SSID
	 */
	public void updateClientLvap(WirelessClient oc);
	
	/**
	 * Send probe response frame through agent
	 * @param clientHwAddr 
	 * @param bssid
	 * @param ssidLists
	 */
	public void sendProbeResponse(MacAddress clientHwAddr, MacAddress bssid, Set<String> ssidLists);
	
	/**
	 * Returns timestamp of last heartbeat from agent
	 * @return Timestamp
	 */
	public long getLastHeard (); 
	
	
	/**
	 * Set the lastHeard timestamp of a client
	 * @param t timestamp to update lastHeard value
	 */
	public void setLastHeard (long t);
	
	
	/**
	 * Set subscriptions
	 * @param subscriptions 
	 * @param t timestamp to update lastHeard value
	 */
	public void setSubscriptions (String subscriptionList);
	
	/**
	 * Get station's RX aggregation infomation from ap's system kernel. 
	 * 
	 * @return a list of WirelessClient entities on the agent
	 */
	public String getStaAggregationRx(WirelessClient client);
	
	/**
	 * Set station's RX aggregation infomation to ap's system kernel. 
	 */
	public void setStaAggregationRx(WirelessClient client, String agg_rx);
}
