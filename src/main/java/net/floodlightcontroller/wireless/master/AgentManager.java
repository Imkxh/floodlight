package net.floodlightcontroller.wireless.master;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;

public class AgentManager {
	
	protected static Logger log = LoggerFactory.getLogger(WirelessMaster.class);
	
	private final ConcurrentHashMap<InetAddress, IApAgent> agentMap = 
			new ConcurrentHashMap<>();
	private IOFSwitchService switchService;
    private final ClientManager clientManager;
    private final PoolManager poolManager;
    
    private final Timer failureDetectionTimer = new Timer();
	private int agentTimeout = 6000;
	
	protected AgentManager (ClientManager clientManager, PoolManager poolManager) {
		this.clientManager = clientManager;
		this.poolManager = poolManager;
	}
	
	protected void setSwitchService(final IOFSwitchService service) {
    	this.switchService = service;
    }
	
	protected void setAgentTimeout (final int timeout) {
    	assert (timeout > 0);
    	agentTimeout = timeout;
    }
	
	/**
	 * Confirm if the agent corresponding to an InetAddress
	 * is being tracked.
	 *
	 * @param WirelessAgentInetAddress
	 * @return true if the agent is being tracked
	 */
	protected boolean isTracked(final InetAddress WirelessAgentInetAddress) {
		return agentMap.containsKey(WirelessAgentInetAddress);
	}
	
	/**
	 * Get the list of agents being tracked for a particular pool
	 * @return agentMap
	 */
	protected Map<InetAddress, IApAgent> getAgents() {
		return Collections.unmodifiableMap(agentMap);
	}
	
	/**
	 * Get a reference to an agent
	 *
	 * @param agentInetAddr
	 */
	protected IApAgent getAgent(final InetAddress agentInetAddr) {
		assert (agentInetAddr != null);
		return agentMap.get(agentInetAddr);
	}
	
	/**
	 * Removes an agent from the agent manager
	 *
	 * @param agentInetAddr
	 */
	protected void removeAgent(InetAddress agentInetAddr) {
		synchronized (this) {
			agentMap.remove(agentInetAddr);
		}
	}
	
	/**
     * Handle a ping from an agent. If an agent was added to the
     * agent map, return true.
     *
     * @param WirelessAgentAddr
     * @return true if an agent was added
     */
	protected boolean receivePing(final InetAddress WirelessAgentAddr) {

    	/*
    	 * If this is not the first time we're hearing from this
    	 * agent, then skip.
    	 */
    	if (WirelessAgentAddr == null || isTracked (WirelessAgentAddr)) {
    		return false;
    	}

    	IOFSwitch ofSwitch = null;

    	
		/*
		 * If the OFSwitch corresponding to the agent has already
		 * registered here, then set it in the WirelessAgent object.
		 * We avoid registering the agent until its corresponding
		 * OFSwitch has done so.
		 */
		for (IOFSwitch sw: switchService.getAllSwitchMap().values()) {
			
			/*
			 * We're binding by IP addresses now, because we want to pool
			 * an OFSwitch with its corresponding WirelessAgent, if any.
			 */
			String switchInetAddr = sw.getInetAddress().toString();
			String switchIpAddr = switchInetAddr.substring(1, switchInetAddr.indexOf(':'));
			
			if (switchIpAddr.equals(WirelessAgentAddr.getHostAddress())) {
				ofSwitch = sw;
				break;
			}
		}

		if (ofSwitch == null)
			return false;

		synchronized (this) {

			/* Possible if a thread has waited
			 * outside this critical region for
			 * too long
			 */
			if (isTracked(WirelessAgentAddr))
				return false;

			IApAgent agent = AgentFactory.getApAgent();
			agent.setSwitch(ofSwitch);
			agent.init(WirelessAgentAddr);
			agent.setLastHeard(System.currentTimeMillis());
			List<String> poolListForAgent = poolManager.getPoolsForAgent(WirelessAgentAddr);

    		/*
    		 * It is possible that the controller is recovering from a failure,
    		 * so query the agent to see what LVAPs it hosts, and add them
    		 * to our client tracker accordingly.
    		 */
    		for (WirelessClient client: agent.getLvapsRemote()) {

    			WirelessClient trackedClient = 
    					clientManager.getClients().get(client.getMacAddress());

    			if (trackedClient == null){
    				clientManager.addClient(client);
    				trackedClient = clientManager.getClients().get(client.getMacAddress());

    				/*
    				 * We need to find the pool the client was previously assigned to.
    				 * The only information we have at this point is the
    				 * SSID list of the client's LVAP. This can be simplified in
    				 * future by adding a "pool" field to the LVAP struct.
    				 */

    				for (String pool: poolListForAgent) {
    					/*
    					 * Every SSID in every pool is unique, so we need to use only one
    					 * of the lvap's SSIDs to find the right pool.
    					 */
    					String ssid = client.getLvap().getSsids().get(0);
    					if (poolManager.getSsidListForPool(pool).contains(ssid)) {
    						poolManager.mapClientToPool(trackedClient, pool);
    						break;
    					}

    				}
    			}

    			if (trackedClient.getLvap().getAgent() == null) {
    				trackedClient.getLvap().setAgent(agent);
    			}
    			else if (!trackedClient.getLvap().getAgent().getIpAddress().equals(WirelessAgentAddr)) {
        			/*
        			 * Race condition:
        			 * - client associated at AP1 before the master failure,
        			 * - master crashes.
        			 * - master re-starts, AP2 connects to the master first.
        			 * - client scans, master assigns it to AP2.
        			 * - AP1 now joins the master again, but it has the client's LVAP as well.
        			 * - Master should now clear the LVAP from AP1.
        			 */
    				agent.removeClientLvap(client);
    			}
    		}

   			agentMap.put(WirelessAgentAddr, agent);

    		log.info("Adding WirelessAgent to map: " + WirelessAgentAddr.getHostAddress());

    		/* This TimerTask checks the lastHeard value
    		 * of the agent in order to handle failure detection
    		 */
    		failureDetectionTimer.scheduleAtFixedRate(new AgentFailureDetectorTask(agent), 1, agentTimeout/2);
		}

		return true;
	}
	
	private class AgentFailureDetectorTask extends TimerTask {
		private final IApAgent agent;
		
		AgentFailureDetectorTask(final IApAgent agent){
			this.agent = agent;
		}
		
		@Override
		public void run() {
			if ((System.currentTimeMillis() - agent.getLastHeard()) >= agentTimeout) {
				log.error("Agent: " + agent.getIpAddress().getHostAddress().toString() + " has timed out");
				
				/* This is default behaviour, maybe we should
				 * re-assign the client based on some specific
				 * behaviour
				 */
				
				// TODO: There should be a way to lock the master
				// during such operations	
				for (WirelessClient oc: agent.getLvapsLocal()) {
					clientManager.getClients().get(oc.getMacAddress()).getLvap().setAgent(null);
				}
				
				// Agent should now be cleared out
				removeAgent(agent.getIpAddress());
				this.cancel();
			}
		}
		
	}

}
