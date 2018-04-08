package net.floodlightcontroller.wireless.web;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import net.floodlightcontroller.wireless.master.IApAgent;
import net.floodlightcontroller.wireless.master.WirelessClient;

public class ClientSerializer extends JsonSerializer<WirelessClient> {

	@Override
	public void serialize(WirelessClient client, JsonGenerator jgen, 
			SerializerProvider provider) throws IOException, JsonProcessingException {
		jgen.writeStartObject();
		jgen.writeStringField("macAddress", client.getMacAddress().toString());
		String clientIpAddr = client.getIpAddress().getHostAddress();
		jgen.writeStringField("ipAddress", clientIpAddr);
		jgen.writeStringField("lvapBssid", client.getLvap().getBssid().toString());
		jgen.writeStringField("lvapSsid", client.getLvap().getSsids().get(0)); // FIXME: assumes single SSID
		jgen.writeNumberField("rssi", client.getStatus().getRssi());
		jgen.writeObjectFieldStart("trans");
		jgen.writeNumberField("rxBytes", client.getStatus().getRxBytes());
		jgen.writeNumberField("rxPackets", client.getStatus().getRxPackets());
		jgen.writeNumberField("txBytes", client.getStatus().getTxBytes());
		jgen.writeNumberField("txPackets", client.getStatus().getTxPacktes());
		jgen.writeEndObject();
		IApAgent agent = client.getLvap().getAgent();
		if (agent != null) 
		{
			jgen.writeStringField("ap", agent.getIpAddress().getHostAddress());
		}
		else 
		{
			jgen.writeStringField("ap", null);
		}	
		jgen.writeEndObject();
		
	}

}
