package net.floodlightcontroller.wireless.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.wireless.master.PoolManager;
import net.floodlightcontroller.wireless.master.WirelessClient;
import net.floodlightcontroller.wireless.master.WirelessMaster;

public class ConnectedClientsResource extends ServerResource {
	
	@Get("json")
    public List<WirelessClient> retreive() {
    	WirelessMaster wm = (WirelessMaster)getContext().getAttributes().
        					get(WirelessMaster.class.getCanonicalName());
    	    	
    	List<WirelessClient> connectedClients = new ArrayList<WirelessClient>();
    	for (WirelessClient e: wm.getClients(PoolManager.GLOBAL_POOL)) {
    		connectedClients.add(e);
    	}
    	
    	return connectedClients;
    }
}
