package net.floodlightcontroller.wireless.master;

import java.net.InetAddress;

import org.projectfloodlight.openflow.types.MacAddress;

public class WirelessClient{
	private final MacAddress hwAddress;
	private InetAddress ipAddress;
	private Lvap lvap;
	private boolean associated;
	private ClientStatus status;
	
	public WirelessClient(MacAddress hwAddress, InetAddress ipAddress, Lvap lvap) {
		super();
		this.hwAddress = hwAddress;
		this.ipAddress = ipAddress;
		this.lvap = lvap;
	}
	
	public MacAddress getMacAddress() {
		return hwAddress;
	}

	public InetAddress getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(InetAddress ipAddress) {
		this.ipAddress = ipAddress;
	}

	public Lvap getLvap() {
		return lvap;
	}

	public void setLvap(Lvap lvap) {
		this.lvap = lvap;
	}

	public boolean isAssociated() {
		return associated;
	}

	public void setAssociated(boolean associated) {
		this.associated = associated;
	}

	public ClientStatus getStatus() {
		return status;
	}

	public void setStatus(ClientStatus status) {
		this.status = status;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof WirelessClient))
			return false;
		if (obj == this)
			return true;
		WirelessClient that = (WirelessClient)obj;
		return (this.hwAddress.equals(that.hwAddress));
	}

	
}
