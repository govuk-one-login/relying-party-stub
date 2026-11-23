package uk.gov.di.handlers;

import com.nimbusds.openid.connect.sdk.claims.ClaimRequirement;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSetRequest;
import io.javalin.http.Context;
import uk.gov.di.config.Configuration;
import uk.gov.di.utils.Oidc;

import java.net.URISyntaxException;
import java.util.List;

public class LandingPageHandler {

    public void handle(Context ctx) throws URISyntaxException {
        var relyingPartyConfig = Configuration.getRelyingPartyConfig(ctx.cookie("relyingParty"));
        var oidcClient = new Oidc(relyingPartyConfig);
        var identityEntry =
                new ClaimsSetRequest.Entry("https://vocab.account.gov.uk/v1/coreIdentityJWT")
                        .withClaimRequirement(ClaimRequirement.ESSENTIAL);

        var authRequest =
                oidcClient.buildQueryParamAuthorizeRequest(
                        relyingPartyConfig.authCallbackUrl(),
                        List.of("Cl.Cm.P2"),
                        List.of("openid", "email", "phone"),
                        new ClaimsSetRequest().add(identityEntry),
                        "",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false);

        ctx.redirect(authRequest.toURI().toString());
    }
}
