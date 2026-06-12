package uk.gov.di.handlers;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignedOutHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SignedOutHandler.class);

    public void handle(Context ctx) {
        LOG.info("Request received in SignedOutHandler");
        ctx.render("/signedout.mustache");
    }
}
