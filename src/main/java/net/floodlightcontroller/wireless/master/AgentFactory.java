package net.floodlightcontroller.wireless.master;

import java.util.ArrayList;
import java.util.List;

public class AgentFactory {
	private static String agentType = "ApAgent";
	private static List<WirelessClient> lvapList = new ArrayList<WirelessClient> ();
	
	public static void setAgentType(String type) {
		if (type.equals("ApAgent") 
				|| type.equals("MockApAgent")) {
			agentType = type;
		}
		else {
			System.err.println("Unknown Agent type: " + type);
			System.exit(-1);
		}
	}
	
	public static void setMockAgentLvapList(List<WirelessClient> list) {
		if (agentType.equals("MockApAgent")) {
			lvapList = list;
		}
	}
	
	public static IApAgent getApAgent() {
		if (agentType.equals("ApAgent")){
			return new ApAgent();
		}
		else if (agentType.equals("MockApAgent")) {
			MockApAgent mock = new MockApAgent();
			
			for (WirelessClient client : lvapList) {
				mock.addClientLvap(client);
			}
			
			return mock;
		}
		
		return null;
	}
}
