package net.floodlightcontroller.wireless.master;

import java.net.InetAddress;
import java.util.Set;

import org.projectfloodlight.openflow.types.MacAddress;

import net.floodlightcontroller.wireless.master.WirelessEventSubscription.EventType;

public interface IApplicationInterface {
	
	/**
	 * VAP-Handoff a client to a new AP. This operation is idempotent.
	 * 
	 * @param newApIpAddr IPv4 address of new access point
	 * @param hwAddrSta Ethernet address of STA to be handed off
	 */
	void handoffClientToAp(String pool, MacAddress staHwAddr, InetAddress newApIpAddr);
	
	/**
	 * Get the list of clients currently registered with Odin
	 * 
	 * @return a map of WirelessClient objects keyed by HW Addresses
	 */
	Set<WirelessClient> getClients(String pool);
	
	/**
	 * Get the WirelessClient type from the client's MacAddress
	 * 
	 * @param pool that the invoking application corresponds to
	 * @return a WirelessClient instance corresponding to clientHwAddress
	 */
	WirelessClient getClientFromHwAddress(String pool, MacAddress clientHwAddress);
		
	/**
	 * Get a list of Odin agents from the agent tracker
	 * @return a map of OdinAgent objects keyed by Ipv4 addresses
	 */
	Set<InetAddress> getAgentAddrs (String pool);
	
	/**
	 * Add a subscription for a particular event defined by oes. cb is
	 * defines the application specified callback to be invoked during
	 * notification. If the application plans to delete the subscription,
	 * later, the onus is upon it to keep track of the subscription
	 * id for removal later.
	 * 
	 * @param oes the susbcription
	 * @param cb the callback
	 */
	void registerSubscription (String Pool, WirelessEventSubscription wes, NotificationCallback cb);
	
	
	/**
	 * Remove a subscription from the list
	 * 
	 * @param id subscription id to remove
	 * @return
	 */
	void unregisterSubscription (String pool, String receiver, EventType eventType);
	
	/**
	 * Application executes a task with an extra thread.
	 * @param r task to be executed
	 */
	void executeApplicationTask(Runnable r);
}
