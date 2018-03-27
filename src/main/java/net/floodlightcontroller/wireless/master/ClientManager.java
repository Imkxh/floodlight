package net.floodlightcontroller.wireless.master;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.projectfloodlight.openflow.types.MacAddress;

public class ClientManager {

	private final Map<MacAddress, WirelessClient> wirelessClientMap = 
			new ConcurrentHashMap<> ();
	
	/**
	 * Add a client to the client tracker
	 * 
	 * @param hwAddress Client's hw address
	 * @param ipv4Address Client's IPv4 address
	 * @param vapBssid Client specific VAP bssid
	 * @param vapEssid Client specific VAP essid
	 */
	protected void addClient (final MacAddress clientHwAddress, final InetAddress ipv4Address, final Lvap lvap) {
		wirelessClientMap.put(clientHwAddress, new WirelessClient (clientHwAddress, ipv4Address, lvap));
	}
	
	
	/**
	 * Add a client to the client tracker
	 * 
	 * @param hwAddress Client's hw address
	 * @param ipv4Address Client's IPv4 address
	 * @param vapBssid Client specific VAP bssid
	 * @param vapEssid Client specific VAP essid
	 */
	protected void addClient (final WirelessClient wc) {
		wirelessClientMap.put(wc.getMacAddress(), wc);
	}
	
	
	/**
	 * Removes a client from the tracker
	 * 
	 * @param hwAddress Client's hw address
	 */
	protected void removeClient (final MacAddress clientHwAddress) {
		wirelessClientMap.remove(clientHwAddress);
	}
	
	
	/**
	 * Get a client by hw address
	 */
	protected WirelessClient getClient (final MacAddress clientHwAddress) {
		return wirelessClientMap.get(clientHwAddress);
	}
	
	
	/**
	 * Get the client Map from the manager
	 * @return client map
	 */
	protected Map<MacAddress, WirelessClient> getClients () {
		return wirelessClientMap;
	}
}
