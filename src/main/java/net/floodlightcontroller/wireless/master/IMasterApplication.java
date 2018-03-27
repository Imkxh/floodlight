package net.floodlightcontroller.wireless.master;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.types.MacAddress;

public interface IMasterApplication {
	
	/**
	 * VAP-Handoff a client to a new AP. This operation is idempotent.
	 * 
	 * @param newApIpAddr IPv4 address of new access point
	 * @param hwAddrSta Ethernet address of STA to be handed off
	 */
	void handoffClientToAp (String pool, MacAddress staHwAddr, InetAddress newApIpAddr);
	
	/**
	 * Set the client's location based on the information returned by the location server.
	 * @param staHwAddr client's mac address
	 * @param location 
	 */
	void setClientLocation (MacAddress staHwAddr, String location);

	/**
	 * Make a connected wireless client witch channels
	 * @param clientHwAddr
	 * @param newApIpAddr
	 * @param switchMode
	 * @param newChannel
	 * @param switchCount
	 */
	void switchClientChannel (final MacAddress clientHwAddr, final InetAddress newApIpAddr, 
			final String switchMode, final String newChannel, final String switchCount);
	/**
	 * Get the list of clients currently registered with Wireless
	 * 
	 * @return a map of WirelessClient objects keyed by HW Addresses
	 */
	Set<WirelessClient> getClients (String pool);
	
	
	/**
	 * Get the WirelessClient type from the client's MacAddress
	 * 
	 * @param pool that the invoking application corresponds to
	 * @return a WirelessClient instance corresponding to clientHwAddress
	 */
	WirelessClient getClientFromHwAddress (String pool, MacAddress clientHwAddress);
	
	/**
	 * Retreive RxStats from the agent
	 * 
	 * @param agentAddr InetAddress of the agent
	 * 
	 * @return Key-Value entries of each recorded statistic for each client 
	 */
	Map<MacAddress, Map<String, String>> getRxStatsFromAgent (String pool, InetAddress agentAddr);
	
	/**
	 * Get a list of Wireless agents from the agent tracker
	 * @return a map of WirelessAgent objects keyed by Ipv4 addresses
	 */
	Set<InetAddress> getAgentAddrs(String pool);
	
	/**
	 * Add an SSID to the Wireless network.
	 * 
	 * @param networkName
	 * @return true if the network could be added, false otherwise
	 */
	boolean addNetwork (String pool, String ssid);
	
	
	/**
	 * Remove an SSID from the Wireless network.
	 * 
	 * @param networkName
	 * @return true if the network could be removed, false otherwise
	 */
	boolean removeNetwork (String pool, String ssid);
	
	/**
	 * Application executes a task with an extra thread.
	 * @param r task to be executed
	 */
	void executeApplicationTask(Runnable r);
}
