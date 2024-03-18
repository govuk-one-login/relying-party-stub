package uk.gov.di.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import uk.gov.di.config.Configuration;
import uk.gov.di.utils.ViewHelper;

import java.util.HashMap;

public class HomeHandler implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(HomeHandler.class);

    @Override
    public Object handle(Request request, Response response) {
        var relyingPartyConfig =
                Configuration.getRelyingPartyConfig(request.cookie("relyingParty"));
        var model = new HashMap<>();
        model.put("servicename", relyingPartyConfig.serviceName());
        LOG.info(
                "Rendering RP with serviceName: {} and clientType: {}",
                relyingPartyConfig.serviceName(),
                relyingPartyConfig.clientType());
        if (relyingPartyConfig.clientType().equals("app")) {
            return ViewHelper.render(model, "app-home.mustache");
        } else {
            return ViewHelper.render(model, "home.mustache");
        }
    }
}
