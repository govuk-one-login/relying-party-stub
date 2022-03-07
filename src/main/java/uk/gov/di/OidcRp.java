package uk.gov.di;

import uk.gov.di.config.RelyingPartyConfig;
import uk.gov.di.handlers.AuthCallbackHandler;
import uk.gov.di.handlers.AuthorizeHandler;
import uk.gov.di.handlers.ErrorHandler;
import uk.gov.di.handlers.HomeHandler;
import uk.gov.di.handlers.SignOutHandler;
import uk.gov.di.handlers.SignedOutHandler;
import uk.gov.di.utils.Oidc;
import uk.gov.di.utils.PrivateKeyReader;

import static spark.Spark.get;
import static spark.Spark.internalServerError;
import static spark.Spark.path;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFileLocation;

public class OidcRp {
    public OidcRp() {
        staticFileLocation("/public");
        port(RelyingPartyConfig.getCloudfoundryPort());

        initRoutes();
    }

    public void initRoutes() {
        var oidcClient =
                new Oidc(
                        RelyingPartyConfig.oidcProviderUrl(),
                        RelyingPartyConfig.clientId(),
                        new PrivateKeyReader(RelyingPartyConfig.clientPrivateKey()));

        var homeHandler = new HomeHandler();
        var authorizeHandler = new AuthorizeHandler(oidcClient);
        var authCallbackHandler = new AuthCallbackHandler(oidcClient);
        var logoutHandler = new SignOutHandler(oidcClient);
        var signedOutHandler = new SignedOutHandler();
        var errorHandler = new ErrorHandler();

        get("/", homeHandler);

        path(
                "/oidc",
                () -> {
                    post("/auth", authorizeHandler);
                    get("/authorization-code/callback", authCallbackHandler);
                });

        post("/logout", logoutHandler);
        get("/signed-out", signedOutHandler);

        internalServerError(errorHandler);
    }
}
