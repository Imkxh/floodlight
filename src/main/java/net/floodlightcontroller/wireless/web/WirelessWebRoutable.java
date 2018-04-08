package net.floodlightcontroller.wireless.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class WirelessWebRoutable implements RestletRoutable{

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/ap/clients/connected", ConnectedClientsResource.class);
		return router;
	}

	@Override
	public String basePath() {
		return "/sdwn";
	}
	
}
