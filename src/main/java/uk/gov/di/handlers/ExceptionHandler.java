package uk.gov.di.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import uk.gov.di.utils.ResponseHeaderHelper;
import uk.gov.di.utils.ViewHelper;

import java.util.HashMap;

public class ExceptionHandler implements spark.ExceptionHandler<Exception> {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);

    @Override
    public void handle(Exception exception, Request request, Response response) {
        LOG.error("Unexpected Error", exception);
        var model = new HashMap<>();
        model.put("error", "Internal Server Error");
        model.put("error_description", exception.getMessage());
        ResponseHeaderHelper.setHeaders(response);
        response.body(ViewHelper.render(model, "error.mustache"));
    }
}
