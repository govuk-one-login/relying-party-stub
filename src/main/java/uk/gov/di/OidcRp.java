package uk.gov.di;

import com.github.mustachejava.DefaultMustacheFactory;
import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinMustache;
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

import static uk.gov.di.config.Configuration.getClientsPublicKeys;

public class OidcRp {
    private static final int DEFAULT_PORT = 8080;

    public OidcRp() {
        var homeHandler = new HomeHandler();
        var authorizeHandler = new AuthorizeHandler();
        var authCallbackHandler = new AuthCallbackHandler();
        var logoutHandler = new SignOutHandler();
        var signedOutHandler = new SignedOutHandler();
        var internalServerErrorHandler = new InternalServerErrorHandler();
        var exceptionHandler = new ExceptionHandler();
        var relyingPartyGetHandler = new RelyingPartyGetHandler();
        var relyingPartyPostHandler = new RelyingPartyPostHandler();

        Javalin.create(
                        config -> {
                            config.staticFiles.add("/public");
                            config.jetty.port = getPort();
                            config.fileRenderer(
                                    new JavalinMustache(new DefaultMustacheFactory("templates")));

                            config.routes.get("/", homeHandler::handle);
                            config.routes.post("/oidc/auth", authorizeHandler::handle);
                            config.routes.get(
                                    "/oidc/authorization-code/callback",
                                    authCallbackHandler::handle);
                            config.routes.post("/logout", logoutHandler::handle);
                            config.routes.get("/signed-out", signedOutHandler::handle);
                            config.routes.post(
                                    "/backchannel-logout", new BackChannelLogoutHandler()::handle);
                            config.routes.get("/relying-party", relyingPartyGetHandler::handle);
                            config.routes.post("/relying-party", relyingPartyPostHandler::handle);
                            getClientsPublicKeys()
                                    .forEach(
                                            clientIdAndPubKey ->
                                                    config.routes.get(
                                                            "/"
                                                                    + clientIdAndPubKey.get(
                                                                            "client_id")
                                                                    + "/.well-known/jwks.json",
                                                            new JwkHandler(clientIdAndPubKey)
                                                                    ::handle));

                            config.routes.exception(Exception.class, exceptionHandler::handle);
                            config.routes.error(500, internalServerErrorHandler::handle);
                            config.routes.after(ResponseHeaderHelper::setHeaders);
                        })
                .start();
    }

    private int getPort() {
        var envPort = System.getenv("PORT");
        return envPort == null ? DEFAULT_PORT : Integer.parseInt(envPort);
    }
}
