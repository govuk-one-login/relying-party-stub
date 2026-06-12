package uk.gov.di.handlers;

import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSet;
import io.javalin.http.Context;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.config.Configuration;
import uk.gov.di.utils.Oidc;

import java.text.ParseException;

import static java.nio.charset.Charset.defaultCharset;
import static java.util.stream.Collectors.toMap;

public class BackChannelLogoutHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BackChannelLogoutHandler.class);

    public void handle(Context ctx) {
        LOG.info("Request received in BackChannelLogoutHandler");
        var relyingPartyConfig = Configuration.getRelyingPartyConfig(ctx.cookie("relyingParty"));
        var oidcClient = new Oidc(relyingPartyConfig);
        var payload =
                URLEncodedUtils.parse(ctx.body(), defaultCharset()).stream()
                        .collect(toMap(NameValuePair::getName, NameValuePair::getValue))
                        .getOrDefault("logout_token", "");
        var useAlternativeDomain = "true".equals(ctx.cookie("useAlternativeDomain"));
        try {
            var jwt = SignedJWT.parse(payload);

            oidcClient
                    .validateLogoutToken(jwt, useAlternativeDomain)
                    .map(ClaimsSet::toJSONString)
                    .ifPresentOrElse(
                            claims -> {
                                LOG.info("Validated logout token. Claims: {}", claims);
                                ctx.status(200);
                            },
                            () -> {
                                LOG.error("Unable to validate logout token");
                                ctx.status(400);
                            });

        } catch (ParseException e) {
            LOG.info("Exception when parsing JWT", e);
            ctx.status(500);
        }

        ctx.result("");
    }
}
