package net.floodlightcontroller.wireless.master;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
import net.floodlightcontroller.wireless.master.WirelessEventSubscription.EventType;
import net.floodlightcontroller.wireless.master.WirelessEventSubscription.SubType;

public class WirelessMaster implements IFloodlightModule, 
					IOFMessageListener, IApplicationInterface{

	protected static Logger log = LoggerFactory.getLogger(WirelessMaster.class);
	
	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	
	private ScheduledExecutorService executor;
	
	private final AgentManager agentManager;
	private final ClientManager clientManager;
	private final LvapManager lvapManager;
	private final PoolManager poolManager;
	
	private final ConcurrentMap<WirelessEventSubscription, NotificationCallback> 
						subscriptions = new ConcurrentHashMap<>();

	private int clientAssocTimeout = 60; // Seconds
	
	static private final String DEFAULT_POOL_FILE = "Poolfile"; 
	static private final int DEFAULT_PORT = 2819;
	
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
	protected synchronized void receiveProbe(final InetAddress agentAddr, final MacAddress clientHwAddress, String ssid) {
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
					
					log.info("Client: " + wc.getMacAddress() + " connecting for first time. "
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
	protected void receiveAssoc(final InetAddress agentAddr, 
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
		client.getLvap().setStaInfo(staInfo);
		subscriptionNotify(EventType.NEW_CLIENT, clientHwAddress.toString());
	}
	
	protected void receiveDisassoc(final InetAddress agentAddr, 
			final MacAddress clientHwAddress, final String reason) {
		
		IApAgent agent = agentManager.getAgent(agentAddr);
		if (agent == null)
			return;
	
		WirelessClient client = clientManager.getClient(clientHwAddress);
		if (client == null)
			return;
		
		if (client.getLvap().getAgent() != agent) {
			log.info("wireless client " + clientHwAddress + 
					" is not associated with agent " + agentAddr);
			return;
		}
		
		log.info("Clearing Lvap " + clientHwAddress +
				" from agent:" + agentAddr + " due to " + reason);
		poolManager.removeClientPoolMapping(client);
		agent.removeClientLvap(client);
		clientManager.removeClient(client.getMacAddress());
	
	}
	
	/**
	 * Handle message about Controller subscribed information sent by ap agent
	 * @param agentAddr agent address
	 * @param clientHwAddress client address
	 * @param eventType subscription event type
	 * @param params subscription event params
	 */
	protected void receivePublish(final InetAddress agentAddr, 
			final MacAddress clientHwAddress, final String eventType, final String params) {
		
		String msg = agentAddr.getHostAddress() + " " + clientHwAddress.toString() + " " + params;
		
		for (WirelessEventSubscription wes : subscriptions.keySet()) {
			if (wes.getEventType().toString().toLowerCase().equals(
					eventType.toLowerCase())) {
				NotificationCallback cb = subscriptions.get(wes);
				assert(cb != null);
				cb.handle(wes.getEventType(), msg);
			}
		}
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
	
	//** Methods provided for the applications(from IApplicationInterface) **//

	
	@Override
	public void handoffClientToAp(String pool, MacAddress staHwAddr, 
			InetAddress newApIpAddr) {
		handoffClientToApInternal(pool, staHwAddr, newApIpAddr);
	}

	@Override
	public Set<WirelessClient> getClients(String pool) {
		return poolManager.getClientsFromPool(pool);
	}

	@Override
	public WirelessClient getClientFromHwAddress(String pool, MacAddress clientHwAddress) {
		WirelessClient client = clientManager.getClient(clientHwAddress);
		return (client != null && poolManager.getPoolForClient(client).equals(pool)) ? client : null;

	}

	@Override
	public Set<InetAddress> getAgentAddrs(String pool) {
		return poolManager.getAgentAddrsForPool(pool);
	}

	@Override
	public void registerSubscription(String pool, WirelessEventSubscription wes, 
			NotificationCallback cb) {
		// FIXME: Need to calculate subscriptions per pool
		assert (wes != null);
		assert (cb != null);

		subscriptions.put(wes, cb);
		
		if (wes.getSubType() == SubType.APAGENT) {
			if (wes.getExtra() != null && !wes.getExtra().equals("")) {
				String sub = wes.getEventType().toString().toLowerCase()
						+ " " + wes.getExtra();
				executor.execute(new AgentPushSubscriptionRunnable(pool, sub));
			}
		}
	}

	@Override
	public void unregisterSubscription(String pool, String receiver, EventType eventType) {
		assert(receiver != null);
		assert(eventType != null);
		assert(!receiver.equals(""));
		
		for (WirelessEventSubscription wes : subscriptions.keySet()) {
			if (wes.getReceiver().equals(receiver) && 
					wes.getEventType() == eventType) {
				subscriptions.remove(wes);
			}
		}
	}
	
	/**
	 * Send subscription message to all subscribers
	 * @param event subscription event type
	 * @param msg subscription message
	 */
	private void subscriptionNotify(WirelessEventSubscription.EventType event, String msg) {
		assert (event != null);
		
		for (Entry<WirelessEventSubscription, NotificationCallback> entry : 
			subscriptions.entrySet()) {
			if (entry.getKey().getEventType().equals(event)) {
				entry.getValue().handle(event, msg);
			}
		}
	}
	
	@Override
	public void executeApplicationTask(Runnable r) {
		assert (r != null);
		executor.execute(r);
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
		
		try {
			readPoolFile(context);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	private void readPoolFile(FloodlightModuleContext context) 
			throws UnknownHostException, InstantiationException, 
			IllegalAccessException, ClassNotFoundException, IOException {
		
		// read config options
        Map<String, String> configOptions = context.getConfigParams(this);
        
        // List of trusted agents
        String agentAuthListFile = DEFAULT_POOL_FILE;
        String agentAuthListFileConfig = configOptions.get("PoolFile");
        
        if (agentAuthListFileConfig != null) {
        	agentAuthListFile = agentAuthListFileConfig; 
        }
        
        List<WirelessApplication> applicationList = 
        		new ArrayList<WirelessApplication>();
		BufferedReader br = new BufferedReader(
				new FileReader(agentAuthListFile));
		
		String strLine;
		
		/* Each line has the following format:
		 * 
		 * IPAddr-of-agent  pool1 pool2 pool3 ...
		 */
		while ((strLine = br.readLine()) != null) {
			if (strLine.startsWith("#")) // comment
				continue;
			
			if (strLine.length() == 0) // blank line
				continue;
			
			// NAME
			String [] fields = strLine.split(" "); 
			if (!fields[0].equals("NAME")) {
				log.error("Missing NAME field " + fields[0]);
				log.error("Offending line: " + strLine);
				System.exit(1);
			}
			
			if (fields.length != 2) {
				log.error("A NAME field should specify a single string as a pool name");
				log.error("Offending line: " + strLine);
				System.exit(1);
			}

			String poolName = fields[1];
			System.out.println("pool:"+poolName);				
			// NODES
			strLine = br.readLine();
			
			if (strLine == null) {
				log.error("Unexpected EOF after NAME field for pool: " + poolName);
				System.exit(1);
			}
			
			fields = strLine.split(" ");
			
			if (!fields[0].equals("NODES")){
				log.error("A NAME field should be followed by a NODES field");
				log.error("Offending line: " + strLine);
				System.exit(1);
			}
			
			if(fields.length == 1) {				
				log.error("A pool must have at least one node defined for it");
				log.error("Offending line: " + strLine);
				System.exit(1);
			}
			
			for (int i = 1; i < fields.length; i++) {
				poolManager.addPoolForAgent(InetAddress.getByName(fields[i]), poolName);
				System.out.println(fields[i]);
			}
			
			// NETWORKS
			strLine = br.readLine();
			
			if (strLine == null) {
				log.error("Unexpected EOF after NODES field for pool: " + poolName);
				System.exit(1);
			}

			fields = strLine.split(" ");
			
			if (!fields[0].equals("NETWORKS")) {
				log.error("A NODES field should be followed by a NETWORKS field");
				log.error("Offending line: " + strLine);
				System.exit(1);
			}
			
			for (int i = 1; i < fields.length; i++) {
				poolManager.addNetworkForPool(poolName, fields[i]);
				System.out.println(fields[i]);					
			}
			
			// APPLICATIONS
			strLine = br.readLine();
			
			if (strLine == null) {
				log.error("Unexpected EOF after NETWORKS field for pool: " + poolName);
				System.exit(1);
			}

			fields = strLine.split(" ");
			
			if (!fields[0].equals("APPLICATIONS")) {
				log.error("A NETWORKS field should be followed by an APPLICATIONS field");
				log.error("Offending line: " + strLine);
				System.exit(1);
			}
			
			for (int i = 1; i < fields.length; i++) {
				WirelessApplication appInstance = (WirelessApplication) Class.forName(fields[i]).newInstance();
				appInstance.setWirelessInterface(this);
				appInstance.setPool(poolName);
				applicationList.add(appInstance);
			}
		}
		
		br.close();
		
		// Spawn applications
        for (WirelessApplication app: applicationList) {
        	if (app == null) {
				System.out.println("nullllllllllllllllllllll");

        	}
        	executor.execute(app);
        }
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        executor.execute(new AgentMsgServer(this, DEFAULT_PORT, executor));
        agentManager.setSwitchService(switchService);
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
	
	private class AgentPushSubscriptionRunnable implements Runnable {
		
		private final String subscription;
		private final String pool;
		
		AgentPushSubscriptionRunnable(String pool, String subscription) {
			this.pool = pool;
			this.subscription = subscription;
		}
		@Override
		public void run() {
			/**
			 * Should probably have threads to do this
			 */
			for (InetAddress agentAddr : poolManager.getAgentAddrsForPool(pool)) {
				IApAgent agent = agentManager.getAgent(agentAddr);
				if (agent != null) {
					agent.setSubscriptions(subscription);
				}
			}
		}
		
	}

}
