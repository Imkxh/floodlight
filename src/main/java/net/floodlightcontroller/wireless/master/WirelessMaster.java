package net.floodlightcontroller.wireless.master;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class WirelessMaster implements IFloodlightModule, IOFMessageListener, IMasterApplication{

	protected static Logger log = LoggerFactory.getLogger(WirelessMaster.class);
	
	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	
	private ScheduledExecutorService executor;
	
	private final AgentManager agentManager;
	private final ClientManager clientManager;	
	private final LvapManager lvapManager;
	private final PoolManager poolManager; 
	
	private int clientAssocTimeout = 60; // Seconds
	
	public WirelessMaster(){
		clientManager = new ClientManager();
		lvapManager = new LvapManager();
		poolManager = new PoolManager();
		agentManager = new AgentManager(clientManager, poolManager);
	}
	
	public WirelessMaster(AgentManager agentManager, ClientManager clientManager, 
			LvapManager lvapManager, PoolManager poolManager) {
		this.agentManager = agentManager;
		this.clientManager = clientManager;
		this.lvapManager = lvapManager;
		this.poolManager = poolManager;
	}
	//************** Wireless Agent->Master protocol handlers ***************//
	
	/**
	 * Handle a ping from an agent
	 * 
	 * @param InetAddress of the agent
	 */
	protected void receivePing(final InetAddress agentAddr) {
		if (agentManager.receivePing(agentAddr)) {
						
		}
		else {
			updateAgentLastHeard (agentAddr);
		}
	}
	
	/**
	 * Handle a probe message from an agent, triggered
	 * by a particular client.
	 * 
	 * @param agentAddr InetAddress of agent
	 * @param clientHwAddress MAC address of client that performed probe scan
	 */
	protected void receiveProbe(final InetAddress agentAddr, final MacAddress clientHwAddress, String ssid) {
		if (agentAddr == null 
			|| clientHwAddress == null 
			|| clientHwAddress.isBroadcast()
			|| clientHwAddress.isMulticast() 
			|| agentManager.isTracked(agentAddr) == false
			|| poolManager.getNumNetworks() == 0) {
			return;
		}
		
		updateAgentLastHeard(agentAddr);
			
		/*
		 * If clients perform an active scan, generate
		 * probe responses without spawning lvaps
		 */
		if (ssid.equals("")) {
			// we just send probe responses
			IApAgent agent = agentManager.getAgent(agentAddr);
			MacAddress bssid = poolManager.generateBssidForClient(clientHwAddress);
			
			// FIXME: Sub-optimal. We'll end up generating redundant probe requests
			Set<String> ssidSet = new TreeSet<String> ();
			for (String pool: poolManager.getPoolsForAgent(agentAddr)) {
				if (pool.equals(PoolManager.GLOBAL_POOL)) 
					continue;
				
				ssidSet.addAll(poolManager.getSsidListForPool(pool));
			}
			executor.execute(new AgentSendProbeResponseRunnable(
					agent, clientHwAddress, bssid, ssidSet));
			return;
		}
					
		/*
		 * Client is scanning for a particular SSID. Verify
		 * which pool is hosting the SSID, and assign
		 * an LVAP into that pool
		 */
		for (String pool: poolManager.getPoolsForAgent(agentAddr)) {
			if (poolManager.getSsidListForPool(pool).contains(ssid)) {
				WirelessClient wc = clientManager.getClient(clientHwAddress);
						    	
			    // Hearing from this client for the first time
			    if (wc == null) {		    		
					List<String> ssidList = new ArrayList<String> ();
					ssidList.addAll(poolManager.getSsidListForPool(pool));
						
					Lvap lvap = new Lvap (poolManager.generateBssidForClient(clientHwAddress), ssidList);
					try {
						wc = new WirelessClient(clientHwAddress, InetAddress.getByName("0.0.0.0"), lvap);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
			    	clientManager.addClient(wc);
			    }
			    	
			    Lvap lvap = wc.getLvap();
			    assert (lvap != null);
			    
			    /* If the client is connecting for the first time, then it
				 * doesn't have a agent associated with it already
				 */
				if (lvap.getAgent() == null) {
					IApAgent agent = agentManager.getAgent(agentAddr);
					
					// Push flow messages associated with the client
					/*try {
						newAgent.getSwitch().write(lvap.getOFMessageList(), null);
					} catch (IOException e) {
						log.error("Failed to update switch's flow tables " + newAgent.getSwitch());
					}*/

					agent.addClientLvap(wc);
					lvap.setAgent(agent);
					executor.schedule(new ClientAssocTimeOutTask(wc), 
							clientAssocTimeout, TimeUnit.SECONDS);
					
					log.info ("Client: " + wc.getMacAddress() + " connecting for first time. "
							+ "Assigning to: " + agent.getIpAddress());
				}

				poolManager.mapClientToPool(wc, pool);
				
				return;
			}
		}		
	}
	
	/**
	 * Receive associated information after client was associated with vap.
	 * @param agentAddr
	 * @param clientHwAddress
	 * @param staInfo
	 */
	protected void receiveAssociatedInfo(final InetAddress agentAddr, 
			final MacAddress clientHwAddress, String staInfo) {
		if (agentAddr == null || clientHwAddress == null 
				|| staInfo == null || staInfo.equals("")) {
			return;
		}
		
		WirelessClient client = clientManager.getClient(clientHwAddress);
		if (client == null) {
			log.error("WirelessClient " + clientHwAddress + "doesn't exist");
			return;
		}
		if (!client.getLvap().getAgent().getIpAddress().equals(agentAddr)) {
			log.error("WirelessClient " + clientHwAddress + "is not associated "
					+ "with WirelessAgent " + agentAddr);
			return;
		}
		
		client.setAssociated(true);
		client.setStaInfo(staInfo);
	}
	
	/**
	 * VAP-Handoff a client to a new AP. This operation is idempotent.
	 *
	 * @param newApIpAddr IPv4 address of new access point
	 * @param hwAddrSta Ethernet address of STA to be handed off
	 */
	private void handoffClientToApInternal (String pool, final MacAddress clientHwAddr, 
			final InetAddress newApIpAddr){
		
		// As an optimisation, we probably need to get the accessing done first,
		// prime both nodes, and complete a handoff.

		if (pool == null || clientHwAddr == null || newApIpAddr == null) {
			log.error("null argument in handoffClientToAp(): pool: " + pool + "clientHwAddr: " + clientHwAddr + " newApIpAddr: " + newApIpAddr);
			return;
		}
		
		synchronized (this) {
			IApAgent newAgent = agentManager.getAgent(newApIpAddr);
			if (newAgent == null) {
				log.error("Handoff request ignored: WirelessAgent " + newApIpAddr + " doesn't exist");
				return;
			}
			
			WirelessClient client = clientManager.getClient(clientHwAddr);
			if (client == null) {
				log.error("Handoff request ignored: WirelessClient " + clientHwAddr + " doesn't exist");
				return;
			}
			
			Lvap lvap = client.getLvap();
			assert(lvap != null);
			
			/* If the client is already associated with AP-newIpAddr, we ignore
			 * the request.
			 */
			InetAddress currentApIpAddress = lvap.getAgent().getIpAddress();
			if (currentApIpAddress.getHostAddress().equals(newApIpAddr.getHostAddress())) {
				log.info ("Client " + clientHwAddr + " is already associated with AP " + newApIpAddr);
				return;
			}
			
			/* Verify permissions.
			 * 
			 * - newAP and oldAP should both fall within the same pool.
			 * - client should be within the same pool as the two APs.
			 * - invoking application should be operating on the same pools
			 *  
			 * By design, this prevents handoffs within the scope of the
			 * GLOBAL_POOL since that would violate a lot of invariants
			 * in the rest of the system.
			 */
			String clientPool = poolManager.getPoolForClient(client);
			if (clientPool == null || !clientPool.equals(pool)) {
				log.error ("Cannot handoff client '" + client.getMacAddress() + "' from " + clientPool + " domain when in domain: '" + pool + "'");
			}
			
			if (! (poolManager.getPoolsForAgent(newApIpAddr).contains(pool)
					&& poolManager.getPoolsForAgent(currentApIpAddress).contains(pool)) ){
				log.info ("Agents " + newApIpAddr + " and " + currentApIpAddress + " are not in the same pool: " + pool);
				return;
			}
			
			// Push flow messages associated with the client
			/*try {
				newAgent.getSwitch().write(lvap.getOFMessageList(), null);
			} catch (IOException e) {
				log.error("Failed to update switch's flow tables " + newAgent.getSwitch());
			}*/

			/* Client is with another AP. We remove the VAP from
			 * the current AP of the client, and spawn it on the new one.
			 * We split the add and remove VAP operations across two threads
			 * to make it faster. Note that there is a temporary inconsistent
			 * state between setting the agent for the client and it actually
			 * being reflected in the network
			 */
			lvap.setAgent(newAgent);
			executor.execute(new AgentLvapAddRunnable(newAgent, client));
			executor.execute(new AgentLvapRemoveRunnable(agentManager.getAgent(currentApIpAddress), client));			
		}
	}
 
	
	private void updateAgentLastHeard(InetAddress agentAddr) {
		IApAgent agent = agentManager.getAgent(agentAddr);
		
		if (agent != null) {
			// Update last-heard for failure detection
			agent.setLastHeard(System.currentTimeMillis());
		}
	}
	
	//********* applications methods (from IMasterToApplicationInterface) **********//
	
	@Override
	public void handoffClientToAp(String pool, MacAddress staHwAddr, InetAddress newApIpAddr) {
		handoffClientToApInternal(pool, staHwAddr, newApIpAddr);
	}

	@Override
	public void setClientLocation(MacAddress staHwAddr, String location) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchClientChannel(MacAddress clientHwAddr, InetAddress newApIpAddr, String switchMode,
			String newChannel, String switchCount) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Set<WirelessClient> getClients(String pool) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public WirelessClient getClientFromHwAddress(String pool, MacAddress clientHwAddress) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<MacAddress, Map<String, String>> getRxStatsFromAgent(String pool, InetAddress agentAddr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<InetAddress> getAgentAddrs(String pool) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean addNetwork(String pool, String ssid) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeNetwork(String pool, String ssid) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void executeApplicationTask(Runnable r) {
		// TODO Auto-generated method stub
		
	}
	
	
	//******************** IFloodlightModule methods ********************//
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
		        new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		IThreadPoolService tp = context.getServiceImpl(IThreadPoolService.class);
		executor = tp.getScheduledExecutor();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        executor.execute(new AgentMsgServer(this, 2819, executor));
	}

	//******************** IOFMessageListener methods ********************//
	
	@Override
	public String getName() {
		return WirelessMaster.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		return Command.CONTINUE;
	}
	
	
	
	
	
	
	private class ClientAssocTimeOutTask implements Runnable {
		private final WirelessClient oc;

		ClientAssocTimeOutTask(final WirelessClient oc) {
			this.oc = oc;
		}

		@Override
		public void run() {
			WirelessClient client = clientManager.getClients().get(oc.getMacAddress());

			if (client == null) {
				return;
			}

			// Client didn't follow through to connect - no assoc message received in the master

			if(client.isAssociated() == false){
				IApAgent agent = client.getLvap().getAgent();

				if (agent != null) {
					log.info("Clearing Lvap " + client.getMacAddress() +
							" from agent:" + agent.getIpAddress() + " due to association not completed");
					poolManager.removeClientPoolMapping(client);
					agent.removeClientLvap(client);
					clientManager.removeClient(client.getMacAddress());
				}

			 }
		}
	}

	private class AgentLvapAddRunnable implements Runnable {
		final IApAgent agent;
		final WirelessClient client;

		AgentLvapAddRunnable(IApAgent newAgent, WirelessClient wc) {
			this.agent = newAgent;
			this.client = wc;
		}
		@Override
		public void run() {
			agent.addClientLvap(client);
		}

	}

	private class AgentLvapRemoveRunnable implements Runnable {
		final IApAgent agent;
		final WirelessClient client;

		AgentLvapRemoveRunnable(IApAgent wa, WirelessClient wc) {
			this.agent = wa;
			this.client = wc;
		}
		@Override
		public void run() {
			agent.removeClientLvap(client);
		}

	}
	
	private class AgentSendProbeResponseRunnable implements Runnable {
		
		final IApAgent agent;
		final MacAddress clientHwAddr;
		final MacAddress bssid;
		final Set<String> ssidList;
		
		AgentSendProbeResponseRunnable(IApAgent agent, MacAddress clientHwAddr, 
				MacAddress bssid, Set<String> ssidList) {
			this.agent = agent;
			this.clientHwAddr = clientHwAddr;
			this.bssid = bssid;
			this.ssidList = ssidList;
		}
		@Override
		public void run() {
			agent.sendProbeResponse(clientHwAddr, bssid, ssidList);
		}
		
	}
}
