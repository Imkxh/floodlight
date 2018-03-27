package net.floodlightcontroller.wireless.master;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.projectfloodlight.openflow.types.MacAddress;

import net.floodlightcontroller.core.IOFSwitch;

public class MockApAgent implements IApAgent{

	private IOFSwitch sw = null;
	private InetAddress ipAddr = null;
	private long lastHeard;
	private ConcurrentSkipListSet<WirelessClient> clientList = new ConcurrentSkipListSet<WirelessClient>();
	
	@Override
	public InetAddress getIpAddress() {
		return ipAddr;
	}

	@Override
	public Set<WirelessClient> getLvapsRemote() {
		return clientList;
	}

	@Override
	public Set<WirelessClient> getLvapsLocal() {
		return clientList;
	}

	@Override
	public Map<MacAddress, Map<String, String>> getRxStats() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int init(InetAddress host) {
		this.ipAddr = host;
		return 0;
	}

	@Override
	public IOFSwitch getSwitch() {
		return sw;
	}

	@Override
	public void setSwitch(IOFSwitch sw) {
		this.sw = sw;
	}

	@Override
	public void updateRssiFilterExpress(String express) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeClientLvap(WirelessClient oc) {
		clientList.remove(oc);
	}

	@Override
	public void addClientLvap(WirelessClient oc) {
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
	public void updateClientLvap(WirelessClient oc) {
		clientList.add(oc);
	}

	@Override
	public void sendProbeResponse(MacAddress clientHwAddr, MacAddress bssid, Set<String> ssidLists) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public long getLastHeard() {
		return lastHeard;
	}

	@Override
	public void setLastHeard(long t) {
		this.lastHeard = t;
	}

	@Override
	public void setSubscriptions(String subscriptionList) {
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
