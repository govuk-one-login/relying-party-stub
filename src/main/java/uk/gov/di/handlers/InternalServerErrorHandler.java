package uk.gov.di.handlers;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.utils.ResponseHeaderHelper;

import java.util.HashMap;

public class InternalServerErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(InternalServerErrorHandler.class);

    public void handle(Context ctx) {
        LOG.error(
                "ErrorResponse received. Error: {}, Error Description: {}",
                ctx.queryParam("error"),
                ctx.queryParam("error_description"));
        var model = new HashMap<String, Object>();
        model.put("error", ctx.queryParam("error"));
        model.put("error_description", ctx.queryParam("error_description"));
        ResponseHeaderHelper.setHeaders(ctx);
        ctx.render("/error.mustache", model);
    }
}
