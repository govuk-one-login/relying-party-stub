package uk.gov.di;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.handlers.AuthCallbackHandler;
import uk.gov.di.handlers.AuthorizeHandler;
import uk.gov.di.handlers.BackChannelLogoutHandler;
import uk.gov.di.handlers.ExceptionHandler;
import uk.gov.di.handlers.HomeHandler;
import uk.gov.di.handlers.InternalServerErrorHandler;
import uk.gov.di.handlers.JwkHandler;
import uk.gov.di.handlers.RelyingPartyGetHandler;
import uk.gov.di.handlers.RelyingPartyPostHandler;
import uk.gov.di.handlers.SignOutHandler;
import uk.gov.di.handlers.SignedOutHandler;
import uk.gov.di.utils.ResponseHeaderHelper;

import static spark.Spark.after;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.internalServerError;
import static spark.Spark.path;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFileLocation;
import static uk.gov.di.config.Configuration.getClientsPublicKeys;

public class OidcRp {
    private static final Logger LOG = LoggerFactory.getLogger(OidcRp.class);

    public OidcRp() {
        staticFileLocation("/public");
        port(8080);

        initRoutes();
    }

    public void initRoutes() {

        var homeHandler = new HomeHandler();
        var authorizeHandler = new AuthorizeHandler();
        var authCallbackHandler = new AuthCallbackHandler();
        var logoutHandler = new SignOutHandler();
        var signedOutHandler = new SignedOutHandler();
        var internalServerErrorHandler = new InternalServerErrorHandler();
        var exceptionHandler = new ExceptionHandler();
        var relyingPartyGetHandler = new RelyingPartyGetHandler();
        var relyingPartyPostHandler = new RelyingPartyPostHandler();
        var gson = new Gson();

        get("/", homeHandler);
        path(
                "/oidc",
                () -> {
                    post("/auth", authorizeHandler);
                    get("/authorization-code/callback", authCallbackHandler);
                });

        post("/logout", logoutHandler);
        get("/signed-out", signedOutHandler);
        post("/backchannel-logout", new BackChannelLogoutHandler());
        get("/relying-party", relyingPartyGetHandler);
        post("/relying-party", relyingPartyPostHandler);
        getClientsPublicKeys()
                .forEach(
                        clientIdAndPubKey -> {
                            LOG.info("clientIdAndPubKey: " + clientIdAndPubKey);
                            get(
                                    "/"
                                            + clientIdAndPubKey.get("client_id")
                                            + "/.well-known/jwks.json",
                                    new JwkHandler(clientIdAndPubKey),
                                    gson::toJson);
                        });

        exception(Exception.class, exceptionHandler);

        internalServerError(internalServerErrorHandler);

        after("/*", (req, res) -> ResponseHeaderHelper.setHeaders(res));
    }
}
