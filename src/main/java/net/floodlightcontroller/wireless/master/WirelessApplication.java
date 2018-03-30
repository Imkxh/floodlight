package net.floodlightcontroller.wireless.master;

import java.net.InetAddress;
import java.util.Set;

import org.projectfloodlight.openflow.types.MacAddress;

import net.floodlightcontroller.wireless.master.WirelessEventSubscription.EventType;

public abstract class WirelessApplication implements Runnable{
	
	private IApplicationInterface applicationInterface;
	private String pool;
	
	/**
	 * Set the WirelessMaster to use
	 */
	final void setWirelessInterface (IApplicationInterface application) {
		applicationInterface = application;
	}
	
	/**
	 * Sets the pool to use for the application
	 * @param pool
	 */
	final void setPool (String pool) {
		this.pool = pool;
	}
	
	
	/**
	 * Needed to wrap OdinApplications into a thread, and is
	 * implemented by the specific application
	 */
	public abstract void run();
	
	/**
	 * VAP-Handoff a client to a new AP. This operation is idempotent.
	 * 
	 * @param newApIpAddr IPv4 address of new access point
	 * @param hwAddrSta Ethernet address of STA to be handed off
	 */
	protected final void handoffClientToAp (MacAddress staHwAddr, InetAddress newApIpAddr) {
		applicationInterface.handoffClientToAp(pool, staHwAddr, newApIpAddr);
	}
	
	/**
	 * Get the WirelessClient type from the client's MACAddress
	 * 
	 * @return a WirelessClient instance corresponding to clientHwAddress
	 */
	protected final WirelessClient getClientFromHwAddress (MacAddress clientHwAddress) {
		return applicationInterface.getClientFromHwAddress(pool, clientHwAddress);
	}
	
	/**
	 * Get the list of clients currently registered with Odin
	 * 
	 * @return a map of WirelessClient objects keyed by HW Addresses
	 */
	protected final Set<WirelessClient> getClients () {
		return applicationInterface.getClients(pool);		
	}
	
	/**
	 * Get a list of Odin agents from the agent tracker
	 * @return a map of OdinAgent objects keyed by Ipv4 addresses
	 */
	protected final Set<InetAddress> getAgents () {
		return applicationInterface.getAgentAddrs(pool);
	}
	
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
	protected final void registerSubscription (WirelessEventSubscription wes, NotificationCallback cb){
		applicationInterface.registerSubscription(pool, wes, cb);
	}
	
	
	/**
	 * Remove a subscription from the list
	 * 
	 * @param id subscription id to remove
	 * @return
	 */
	protected final void unregisterSubscription (String receiver, EventType eventType) {
		applicationInterface.unregisterSubscription(pool, receiver, eventType);
	}
	
	/**
	 * Application executes a task with an extra thread.
	 * @param r task to be executed
	 */
	protected final void executeTask(Runnable r) {
		applicationInterface.executeApplicationTask(r);
	}
}
