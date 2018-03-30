package net.floodlightcontroller.wireless.master;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortFeatures;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockSwitchManager;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.statistics.StatisticsCollector;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.wireless.applications.MobilityManager;
import net.floodlightcontroller.wireless.master.WirelessEventSubscription.EventType;
import net.floodlightcontroller.wireless.master.WirelessEventSubscription.SubType;

public class WirelessTest extends FloodlightTestCase{
	
	private FloodlightContext cntx;
    private FloodlightModuleContext fmc;
    private MockThreadPoolService threadpool;
    private IOFSwitchService switchService;
    protected AgentManager agentManager;
    protected ClientManager clientManager;
    protected WirelessMaster wirelessMaster;
    protected LvapManager lvapManager;
    protected PoolManager poolManager;
    
    protected long switchId = 1L;
    
    /**
     * Use this to add a mock agent on IP:ipAddress and port:port.
     * 
     * @param ipAddress
     * @param port
     * @throws Exception
     */
    private void addAgentWithMockSwitch (String ipAddress, int port) throws Exception {
        int size = agentManager.getAgents().size();
    	
        agentManager.receivePing(InetAddress.getByName(ipAddress));
        
    //	assertEquals(agentManager.getAgents().size(), size);

    	// Now register a switch
        long id = switchId++;      
        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        OFFactory inputFactory = OFFactories.getFactory(OFVersion.OF_13);
        InetSocketAddress sa = new InetSocketAddress(ipAddress, port);
        reset(sw);
        expect(sw.getId()).andReturn(DatapathId.of(id)).anyTimes();
        expect(sw.getOFFactory()).andReturn(inputFactory).anyTimes();
        expect(sw.getInetAddress()).andReturn(sa).anyTimes();
        EasyMock.replay(sw);
        
        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        getMockSwitchService().setSwitches(switchMap);
        switchMap.put(sw.getId(), sw);
        
        agentManager.receivePing(InetAddress.getByName(ipAddress));
        assertEquals(agentManager.getAgents().size(),size + 1);    
       // assertEquals(agentManager.getAgents().get(InetAddress.getByName(ipAddress)).getSwitch(), sw);
    }
    
    private void addClientToClientManagerSingleSsid (MacAddress sta, 
    		InetAddress inetAddr, MacAddress lvapBssid, String ssid) {
    	ArrayList<String> ssidList = new ArrayList<String> ();
    	ssidList.add(ssid);
    	Lvap lvap = new Lvap (lvapBssid, ssidList);
    	clientManager.addClient(sta, inetAddr, lvap);
    }
    
    @Before
    public void setup() throws Exception{
        
    	super.setUp();

        cntx = new FloodlightContext();
        fmc = new FloodlightModuleContext();
        clientManager = new ClientManager();
        poolManager = new PoolManager();
        agentManager = new AgentManager(clientManager, poolManager);
        lvapManager = new LvapManager();
        wirelessMaster = new WirelessMaster(agentManager, 
        		clientManager, lvapManager, poolManager);
        
        // Module loader setup
        threadpool = new MockThreadPoolService();
        switchService = getMockSwitchService();

        fmc.addService(IThreadPoolService.class, threadpool);
        fmc.addService(IOFSwitchService.class, switchService);
    	
        agentManager.setSwitchService(switchService);
        AgentFactory.setAgentType("MockApAgent");
        wirelessMaster.init(fmc);
        
    }
    
    /************* Master Tests ******************/
    /**
     * Make sure the client tracker doesn't duplicate
     * client MAC addresses.
     * 
     * @throws Exception
     */
    @Test
    public void testClientTracker() throws Exception {
    	assertEquals(clientManager.getClients().size(),0);
    	addClientToClientManagerSingleSsid(MacAddress.of("00:00:00:00:00:01"),
								 InetAddress.getByName("172.17.1.1"),
								 MacAddress.of("00:00:00:00:00:01"),
								 "sdwn");
		
		assertEquals(clientManager.getClients().size(),1);
		addClientToClientManagerSingleSsid(MacAddress.of("00:00:00:00:00:01"),
								 InetAddress.getByName("172.17.1.2"),
								 MacAddress.of("00:00:00:00:00:02"),
								 "sdwn");
		assertEquals(clientManager.getClients().size(),1); // Same hw-addr cant exist twice

		// TODO: None of the other parameters should repeat either!
		clientManager.removeClient(MacAddress.of("00:00:00:00:00:02"));
		assertEquals(clientManager.getClients().size(),1);
		
		clientManager.removeClient(MacAddress.of("00:00:00:00:00:01"));
		assertEquals(clientManager.getClients().size(),0);
		
		clientManager.removeClient(MacAddress.of("00:00:00:00:00:01"));
		assertEquals(clientManager.getClients().size(),0);
    }
    
    /**
     *  Make sure that the agent tracker does not
     *  track an agent if there isn't a corresponding
     *  switch associated with it
     *  
     * @throws Exception
     */
    @Test
    public void testAgentTracker() throws Exception {
    	poolManager.addPoolForAgent(InetAddress.getByName("127.0.0.1"), "pool-1");
    	
        // Send a ping to the OdinAgentTracker to have it
        // register the agent
    	agentManager.receivePing(InetAddress.getByName("127.0.0.1"));
        
        // We haven't registered a switch yet, so this should
        // still be zero
        assertEquals(agentManager.getAgents().size(), 0);
        
        // Now register a switch
        long id = switchId++;      
        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        OFFactory inputFactory = OFFactories.getFactory(OFVersion.OF_13);
        SocketAddress sa = new InetSocketAddress("127.0.0.1", 12345);
        reset(sw);
        expect(sw.getId()).andReturn(DatapathId.of(id)).anyTimes();
        expect(sw.getOFFactory()).andReturn(inputFactory).anyTimes();
        expect(sw.getInetAddress()).andReturn(sa).anyTimes();
        EasyMock.replay(sw);
        
        Map<DatapathId, IOFSwitch> switchMap = new HashMap<>();
        switchMap.put(sw.getId(), sw);
        getMockSwitchService().setSwitches(switchMap);

        // Let's try again
        agentManager.receivePing(InetAddress.getByName("127.0.0.1"));
        
        assertEquals(agentManager.getAgents().size(), 1);
        
        // This agent is not listed in the pool manager's list,
        // so it shouldn't get added.
        agentManager.receivePing(InetAddress.getByName("172.17.5.63"));
        assertEquals(agentManager.getAgents().size(),1);
    }
    
    /**
     * Test to see if OdinAgentTracker.receiveProbe()
     * works correctly with a single SSID
     * 
     * @throws Exception
     */
    @Test
    public void testReceiveProbeSingleSsid() throws Exception {
    	String ipAddress1 = "172.17.2.161";
    	String ipAddress2 = "172.17.2.162";
    	String ipAddress3 = "172.17.2.163";
    	MacAddress clientMacAddr1 = MacAddress.of("00:00:00:00:00:01");
    	MacAddress clientMacAddr2 = MacAddress.of("00:00:00:00:00:02");
    	MacAddress clientMacAddr3 = MacAddress.of("00:00:00:00:00:03");
    	
    	poolManager.addPoolForAgent(InetAddress.getByName(ipAddress1), "pool-1");
		poolManager.addPoolForAgent(InetAddress.getByName(ipAddress2), "pool-1");
		poolManager.addPoolForAgent(InetAddress.getByName(ipAddress3), "pool-1");
		poolManager.addNetworkForPool("pool-1", "sdwn");
  	
    	addAgentWithMockSwitch(ipAddress1, 12345);
    	addAgentWithMockSwitch(ipAddress2, 12345);
    	
    	assertEquals(agentManager.getAgents().size(), 2);
    	
    	// 1. Things shouldn't explode when this is called
    	wirelessMaster.receiveProbe(null, null, null);
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress1), clientMacAddr1, "sdwn");

    	// 2. Client should be added
    	assertEquals(clientManager.getClients().size(), 1);
    	
    	addClientToClientManagerSingleSsid(clientMacAddr1, 
    			InetAddress.getByName("172.17.2.51"), MacAddress.of("00:00:00:00:11:11"), "sdwn");
    	assertEquals(clientManager.getClients().get(clientMacAddr1).getLvap().getAgent(), null);
    	
    	// 3, 4. now try again. Client should be assigned an AP
      	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress1), clientMacAddr1, "sdwn");
      	assertEquals(clientManager.getClients().size(), 1);
    	assertEquals(clientManager.getClients().get(clientMacAddr1).getLvap().getAgent().getIpAddress(), 
    			InetAddress.getByName(ipAddress1));
    	
    	// 5. another probe from the same AP/client should
    	// 	not be handed off, and should not feature
    	// 	in the client list a second time
      	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress1), clientMacAddr1, "sdwn");
    	assertEquals(clientManager.getClients().size(), 1);
    	assertEquals(clientManager.getClients().get(clientMacAddr1).getLvap().getAgent().getIpAddress(), InetAddress.getByName(ipAddress1));
    	
    	// 6. probe scan from new AP. client should not be
    	// handed off to the new one, nor should a new
    	// client be registered.
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress2), clientMacAddr1, "sdwn");
    	assertEquals(clientManager.getClients().size(), 1);
    	assertEquals(clientManager.getClients().get(clientMacAddr1).getLvap().getAgent().getIpAddress(), InetAddress.getByName(ipAddress1));
    	
    	// 7. New client performs a scan at AP1
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress1), clientMacAddr2, "sdwn");
    	assertEquals(clientManager.getClients().size(), 2);
    	
    	// 8. Add client2
    	addClientToClientManagerSingleSsid(clientMacAddr2, InetAddress.getByName("172.17.2.52"), MacAddress.of("00:00:00:00:11:12"), "sdwn");
    	assertEquals(clientManager.getClients().size(), 2);
    	assertEquals(clientManager.getClients().get(clientMacAddr2).getLvap().getAgent(), null);
    	
    	// 9. Receive probe from both APs one after the other.
    	// Client should be assigned to the first AP
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress1), clientMacAddr2, "sdwn");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress2), clientMacAddr2, "sdwn");
    	assertEquals(clientManager.getClients().size(), 2);
    	assertEquals(clientManager.getClients().get(clientMacAddr2).getLvap().getAgent().getIpAddress(), InetAddress.getByName(ipAddress1));
    	
    	
    	// 10. Receive probe from an AP which is yet to register,
    	// for an unauthorised client which is scanning for the first time.
    	// This can occur as a race condition.
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), clientMacAddr3, "sdwn");
    	assertNull(agentManager.getAgents().get(InetAddress.getByName(ipAddress3)));
    	assertEquals(clientManager.getClients().size(), 2);
    	
    	// 11. Add client3
    	addClientToClientManagerSingleSsid(clientMacAddr3, InetAddress.getByName("172.17.2.53"), MacAddress.of("00:00:00:00:11:13"), "sdwn");
    	assertEquals(clientManager.getClients().size(), 3);
    	assertEquals(clientManager.getClients().get(clientMacAddr3).getLvap().getAgent(), null);

    	// 10. Receive probe from an AP which is yet to register,
    	// for an authorised client which is scanning for the first time.
    	// This can occur as a race condition.
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), clientMacAddr3, "sdwn");
    	assertNull(agentManager.getAgents().get(InetAddress.getByName(ipAddress3)));
    	assertEquals(clientManager.getClients().size(), 3);
    	
    	// 11. Now add agent3
    	addAgentWithMockSwitch(ipAddress3, 12345);

    	// 12. Receive probe from an AP which has registered,
    	// for an authorised client which is scanning for the first time.
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), clientMacAddr3, "sdwn");
    	assertEquals(clientManager.getClients().size(), 3);
    	assertEquals(clientManager.getClients().get(clientMacAddr3).getLvap().getAgent().getIpAddress(), InetAddress.getByName(ipAddress3));
    	
    	//wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), null, "sdwn");
    }
    
    /**
     * Test to see if the state updating done at the master
     * is correct when a client attempts to connect to 
     * different SSIDs
     * 
     * @throws Exception
     */
    @Test
    public void testReceiveProbeDifferentSsidsNoOverlappingPools() throws Exception {
    	String ipAddress1 = "172.17.2.161";
    	String ipAddress2 = "172.17.2.162";
    	String ipAddress3 = "172.17.2.163";
    	String ipAddress4 = "172.17.2.164";
    	MacAddress clientMacAddr1 = MacAddress.of("00:00:00:00:00:01");
    	
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress4), clientMacAddr1, ""); // Shouldn't break anything
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress4), clientMacAddr1, null);
    	
    	poolManager.addPoolForAgent(InetAddress.getByName(ipAddress1), "pool-1");
		poolManager.addPoolForAgent(InetAddress.getByName(ipAddress2), "pool-2");
		poolManager.addPoolForAgent(InetAddress.getByName(ipAddress3), "pool-3");
		poolManager.addNetworkForPool("pool-1", "odin-1");
		poolManager.addNetworkForPool("pool-2", "odin-2");
		poolManager.addNetworkForPool("pool-3", "odin-3");
		
    	addAgentWithMockSwitch(ipAddress1, 12345);
    	addAgentWithMockSwitch(ipAddress2, 12345);
    	addAgentWithMockSwitch(ipAddress3, 12345);
    	
    	/*
    	 * FIXME: Need a way to test if send-probe invocations by the agents are
    	 * correct (as simple as that section of the code is). 
    	 * (Replace StubOdinAgent with a mock maybe?) 
    	 */
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress1), clientMacAddr1, "");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress2), clientMacAddr1, "");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), clientMacAddr1, "");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress4), clientMacAddr1, "");
    	
    	/*
    	 * Client attempts to connect to an ssid we're not hosting.
    	 */
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress1), clientMacAddr1, "nothostingthis");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress2), clientMacAddr1, "nothostingthis");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), clientMacAddr1, "nothostingthis");
    	
    	assertEquals(clientManager.getClients().size(), 0);
    	assertEquals(poolManager.getClientsFromPool(PoolManager.GLOBAL_POOL).size(), 0);
    	
    	/*
    	 * Right ssid, wrong agent(s)
    	 */
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress4), clientMacAddr1, "odin-1");
    	assertEquals(clientManager.getClients().size(), 0);
    	assertEquals(poolManager.getClientsFromPool(PoolManager.GLOBAL_POOL).size(), 0);
    	
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), clientMacAddr1, "odin-1");
    	assertEquals(clientManager.getClients().size(), 0);
    	assertEquals(poolManager.getClientsFromPool(PoolManager.GLOBAL_POOL).size(), 0);

    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress2), clientMacAddr1, "odin-1");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), clientMacAddr1, "odin-1");
    	assertEquals(clientManager.getClients().size(), 0);
    	assertEquals(poolManager.getClientsFromPool(PoolManager.GLOBAL_POOL).size(), 0);

    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress1), clientMacAddr1, "odin-2");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress2), clientMacAddr1, "odin-3");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), clientMacAddr1, "odin-1");
    	assertEquals(clientManager.getClients().size(), 0);
    	assertEquals(poolManager.getClientsFromPool(PoolManager.GLOBAL_POOL).size(), 0);
    	
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress1), clientMacAddr1, "odin");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress2), clientMacAddr1, "odin");
    	wirelessMaster.receiveProbe(InetAddress.getByName(ipAddress3), clientMacAddr1, "odin");
    	assertEquals(clientManager.getClients().size(), 0);
    	assertEquals(poolManager.getClientsFromPool(PoolManager.GLOBAL_POOL).size(), 0);
    }
    
    /**
     * Test to see if the publish subscribe
     * interfaces work correctly when there
     * are multiple applications, each
     * pushing down a single subscription
     * with a single handler.
     * 
     * @throws Exception
     */
    @Test
    public void testSubscriptionsOneToOne() throws Exception {
    	DummyApplication1 app1 = new DummyApplication1();
    	DummyApplication1 app2 = new DummyApplication1();
    	app1.setWirelessInterface(wirelessMaster);
    	app1.run(); // This isn't really a thread, but sets up callbacks
    	
    	String ipAddress1 = "172.17.2.161";
    	MacAddress clientMacAddr1 = MacAddress.of("00:00:00:00:00:01");
    	poolManager.addPoolForAgent(InetAddress.getByName(ipAddress1), "pool-1");
		poolManager.addNetworkForPool("pool-1", "odin");
		
    	agentManager.setAgentTimeout(1000);
    	
    	// Add an agent with no clients associated
    	ConcurrentMap<WirelessEventSubscription, NotificationCallback> subscriptions = 
    			new ConcurrentHashMap<>();
    	
    	addAgentWithMockSwitch(ipAddress1, 12345);

    	/**
    	 * Shouldn't trigger anything
    	 */
    	wirelessMaster.receivePublish(InetAddress.getByName(ipAddress1), clientMacAddr1, 
    			EventType.MOBILITY_SIGNAL.toString(), "msg");
    	assertEquals(app1.counter, 0);
    	assertEquals(app2.counter, 0);
    	
    	/**
    	 * The application app1 should be triggered and app2 not.
    	 */
    	wirelessMaster.receivePublish(InetAddress.getByName(ipAddress1), clientMacAddr1, 
    			EventType.NEW_CLIENT.toString(), "msg");
    	assertEquals(app1.counter, 1);
    	assertEquals(app2.counter, 0);

    	
    }
    
 // Application that registers 1 subscription -> 1 handler
    private class DummyApplication1 extends WirelessApplication {
    	public int counter = 0;
    		
		@Override
		public void run() {
			WirelessEventSubscription signal_sub = new WirelessEventSubscription(
					MobilityManager.class.getName(), SubType.APPLICATION, EventType.NEW_CLIENT);		
			NotificationCallback signal_cb = new NotificationCallback() {

				@Override
				public void handle(EventType type, String msg) {
					callback1(msg);
				}

			};
			registerSubscription(signal_sub, signal_cb);
		}
		
    	private void callback1(String msg){
    		counter += 1;
    	}
    	
    	
    }
}
