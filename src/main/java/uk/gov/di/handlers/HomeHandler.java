package uk.gov.di.handlers;

import io.javalin.http.Context;
import io.javalin.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.config.Configuration;

import java.util.HashMap;

public class HomeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HomeHandler.class);

    public void handle(Context ctx) {
        String relyingPartyString;
        if (ctx.queryParam("relyingParty") != null) {
            relyingPartyString = ctx.queryParam("relyingParty");
            ctx.cookie(new Cookie("relyingParty", relyingPartyString, "/", 3600, false, true));
        } else {
            relyingPartyString = ctx.cookie("relyingParty");
        }

        var relyingPartyConfig = Configuration.getRelyingPartyConfig(relyingPartyString);
        var model = new HashMap<String, Object>();
        model.put("servicename", relyingPartyConfig.serviceName());
        model.put("useAlternativeDomain", "true".equals(ctx.cookie("useAlternativeDomain")));
        LOG.info(
                "Rendering RP with serviceName: {} and clientType: {}",
                relyingPartyConfig.serviceName(),
                relyingPartyConfig.clientType());
        if (relyingPartyConfig.clientType().equals("app")) {
            ctx.render("/app-home.mustache", model);
        } else {
            ctx.render("/home.mustache", model);
        }
    }
}
