package uk.gov.di.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import uk.gov.di.config.Configuration;
import uk.gov.di.utils.Oidc;

import java.util.UUID;

public class SignOutHandler implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(SignOutHandler.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        LOG.info("Generating log out request");
        var relyingPartyConfig =
                Configuration.getRelyingPartyConfig(request.cookie("relyingParty"));
        var oidcClient = new Oidc(relyingPartyConfig);

        var logoutUri =
                oidcClient.buildLogoutUrl(
                        request.cookie("idToken"),
                        UUID.randomUUID().toString(),
                        relyingPartyConfig.postLogoutRedirectUrl());
        response.redirect(logoutUri);
        return null;
    }
}
