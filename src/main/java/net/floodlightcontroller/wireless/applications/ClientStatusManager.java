package net.floodlightcontroller.wireless.applications;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.wireless.master.ClientStatus;
import net.floodlightcontroller.wireless.master.NotificationCallback;
import net.floodlightcontroller.wireless.master.NotificationCallbackContext;
import net.floodlightcontroller.wireless.master.WirelessApplication;
import net.floodlightcontroller.wireless.master.WirelessClient;
import net.floodlightcontroller.wireless.master.WirelessEventSubscription;
import net.floodlightcontroller.wireless.master.WirelessEventSubscription.EventType;
import net.floodlightcontroller.wireless.master.WirelessEventSubscription.SubType;

public class ClientStatusManager extends WirelessApplication{

	protected static Logger log = LoggerFactory.getLogger(ClientStatusManager.class);
	
	public ClientStatusManager () {

	}
	
	/**
	 * Register subscriptions
	 */
	private void init () {
		// Create a subscription that notifies a new client added 
		WirelessEventSubscription sub = new WirelessEventSubscription(
				ClientStatusManager.class.getName(), SubType.APAGENT, EventType.STA_STATUS); 
		 
		NotificationCallback cb = new NotificationCallback() {

			// This will be called when a new client was added
			@Override
			public void handle(EventType type, String msg) {
				updateClientStatus(msg);
			}

		};
		registerSubscription(sub, cb);
		
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
	private synchronized void updateClientStatus(String msg) {
		
		System.out.println("!!!!!!!!!!!!!!!!!!!!!!" + msg);
		String[] fields = msg.split(" ", 3);
		if (fields.length != 3) {
			log.info("updateClientStatus: the number of parameters is wrong");
			return;
		}
		String[] statString = fields[2].split("\n");
		for (String str : statString) {
			String[] param = str.split(" ");
			if (param.length != 9) {
				log.info("updateClientStatus: the client status parameters is wrong");
			}
			WirelessClient client = getClientFromHwAddress(MacAddress.of(param[0]));
			ClientStatus cs = client.getStatus();
			if (cs == null) {
				cs = new ClientStatus();
				client.setStatus(cs);
			}
			cs.setAllStatus(Long.parseLong(param[1]), Long.parseLong(param[2]), 
					Long.parseLong(param[3]), Long.parseLong(param[4]), 
					Long.parseLong(param[5]), Long.parseLong(param[6]),
					Integer.parseInt(param[7]), Integer.parseInt(param[8]));
			
			System.out.println(cs.toString());
		}
		
	}

}
