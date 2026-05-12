package uk.gov.di.handlers;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.config.Configuration;
import uk.gov.di.utils.Oidc;

import java.util.UUID;

public class SignOutHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SignOutHandler.class);

    public void handle(Context ctx) throws Exception {
        LOG.info("Generating log out request");
        var relyingPartyConfig = Configuration.getRelyingPartyConfig(ctx.cookie("relyingParty"));
        var oidcClient = new Oidc(relyingPartyConfig);
        var useAlternativeDomain = "true".equals(ctx.cookie("useAlternativeDomain"));

        var logoutUri =
                oidcClient.buildLogoutUrl(
                        ctx.cookie("idToken"),
                        UUID.randomUUID().toString(),
                        relyingPartyConfig.postLogoutRedirectUrl(),
                        useAlternativeDomain);
        ctx.redirect(logoutUri);
    }
}
