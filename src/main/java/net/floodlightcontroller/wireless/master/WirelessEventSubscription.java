package net.floodlightcontroller.wireless.master;

public class WirelessEventSubscription {
	
	public enum SubType {
		APAGENT,
		APPLICATION;
	};
	
	public enum EventType {
		MOBILITY_SIGNAL,
		NEW_CLIENT;
	};
	
	private String receiver;
	private SubType subType;
	private EventType eventType;
	private String extra;
	
	public String getReceiver() {
		return receiver;
	}
	
	public SubType getSubType() {
		return subType;
	}
	
	public EventType getEventType() {
		return eventType;
	}
	
	public String getExtra() {
		return extra;
	}
	
	public void setExtra(String extra) {
		this.extra = extra;
	}

	public WirelessEventSubscription(String receiver, SubType subType, 
			EventType eventType) {
		this.receiver = receiver;
		this.subType = subType;
		this.eventType = eventType;
	}
	
	
	
}
