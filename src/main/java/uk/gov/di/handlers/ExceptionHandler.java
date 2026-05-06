package uk.gov.di.handlers;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.utils.ResponseHeaderHelper;

import java.util.HashMap;

public class ExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);

    public void handle(Exception exception, Context ctx) {
        LOG.error("Unexpected Error", exception);
        var model = new HashMap<String, Object>();
        model.put("error", "Internal Server Error");
        model.put("error_description", exception.getMessage());
        ResponseHeaderHelper.setHeaders(ctx);
        ctx.render("/error.mustache", model);
    }
}
