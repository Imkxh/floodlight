package net.floodlightcontroller.wireless.master;

import org.projectfloodlight.openflow.types.MacAddress;

public class NotificationCallbackContext {
	public final MacAddress clientHwAddress;
	public final IApAgent agent;
	public final long value;
	
	public NotificationCallbackContext(final MacAddress clientHwAddress, 
			final IApAgent agent, final long value) {
		this.clientHwAddress = clientHwAddress;
		this.agent = agent;
		this.value = value;
	}
}
