package net.floodlightcontroller.wireless.master;

public class ClientStatus {

	private long connectedTime;    //unit is second
	private long inactiveTime;     //unit is ms
	private long rxBytes;
	private long txBytes;
	private long rxPackets;
	private long txPacktes;
	private int rssi;
	private int rssiAvg;
	
	public ClientStatus() {
		
	}

	public ClientStatus(long connectedTime, long inactiveTime, 
			long rxBytes, long txBytes, long rxPackets,
			long txPacktes, int rssi, int rssiAvg) {
		super();
		this.connectedTime = connectedTime;
		this.inactiveTime = inactiveTime;
		this.rxBytes = rxBytes;
		this.txBytes = txBytes;
		this.rxPackets = rxPackets;
		this.txPacktes = txPacktes;
		this.rssi = rssi;
		this.rssiAvg = rssiAvg;
	}
	
	public void setAllStatus(long connectedTime, long inactiveTime, 
			long rxBytes, long txBytes, long rxPackets,
			long txPacktes, int rssi, int rssiAvg) {
		this.connectedTime = connectedTime;
		this.inactiveTime = inactiveTime;
		this.rxBytes = rxBytes;
		this.txBytes = txBytes;
		this.rxPackets = rxPackets;
		this.txPacktes = txPacktes;
		this.rssi = rssi;
		this.rssiAvg = rssiAvg;
	}

	public long getConnectedTime() {
		return connectedTime;
	}

	public void setConnectedTime(long connectedTime) {
		this.connectedTime = connectedTime;
	}

	public long getInactiveTime() {
		return inactiveTime;
	}

	public void setInactiveTime(long inactiveTime) {
		this.inactiveTime = inactiveTime;
	}

	public long getRxBytes() {
		return rxBytes;
	}

	public void setRxBytes(long rxBytes) {
		this.rxBytes = rxBytes;
	}

	public long getTxBytes() {
		return txBytes;
	}

	public void setTxBytes(long txBytes) {
		this.txBytes = txBytes;
	}

	public long getRxPackets() {
		return rxPackets;
	}

	public void setRxPackets(long rxPackets) {
		this.rxPackets = rxPackets;
	}

	public long getTxPacktes() {
		return txPacktes;
	}

	public void setTxPacktes(long txPacktes) {
		this.txPacktes = txPacktes;
	}

	public int getRssi() {
		return rssi;
	}

	public void setRssi(int rssi) {
		this.rssi = rssi;
	}

	public int getRssiAvg() {
		return rssiAvg;
	}

	public void setRssiAvg(int rssiAvg) {
		this.rssiAvg = rssiAvg;
	}

	@Override
	public String toString() {
		return "ClientStatus [connectedTime=" + connectedTime + ", inactiveTime=" + inactiveTime + ", rxBytes="
				+ rxBytes + ", txBytes=" + txBytes + ", rxPackets=" + rxPackets + ", txPacktes=" + txPacktes + ", rssi="
				+ rssi + ", rssiAvg=" + rssiAvg + "]";
	}
	
	
}
