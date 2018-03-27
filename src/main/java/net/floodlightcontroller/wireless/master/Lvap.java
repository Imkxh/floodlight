package net.floodlightcontroller.wireless.master;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.MacAddress;

public class Lvap {
	
	private final MacAddress lvapBssid;
	private final List<String> lvapSsids;
	private String staInfo;
	private IApAgent WirelessAgent;
	private List<OFMessage> msgList = new ArrayList<OFMessage>();

	Lvap(MacAddress bssid, List<String> ssidList) {
		lvapBssid = bssid;
		lvapSsids = ssidList;
	}

	protected void setAgent(IApAgent agent) {
		this.WirelessAgent = agent;
	}

	// ***** Getters and setters ***** //

	public MacAddress getBssid() {
		return lvapBssid;
	}

	public List<String> getSsids() {
		return lvapSsids;
	}

	public IApAgent getAgent() {
		return WirelessAgent;
	}

	public List<OFMessage> getOFMessageList() {
		return msgList;
	}

	public void setOFMessageList(List<OFMessage> msglist) {
		this.msgList = msglist;
	}
	
	public String getStaInfo() {
		return staInfo;
	}

	public void setStaInfo(String staInfo) {
		this.staInfo = staInfo;
	}
}
