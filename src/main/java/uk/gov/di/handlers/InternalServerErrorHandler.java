package uk.gov.di.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import uk.gov.di.utils.ResponseHeaderHelper;
import uk.gov.di.utils.ViewHelper;

import java.util.HashMap;

public class InternalServerErrorHandler implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(InternalServerErrorHandler.class);

    @Override
    public Object handle(Request request, Response response) {
        LOG.error(
                "ErrorResponse received. Error: {}, Error Description: {}",
                request.queryParams("error"),
                request.queryParams("error_description"));
        var model = new HashMap<>();
        model.put("error", request.queryParams("error"));
        model.put("error_description", request.queryParams("error_description"));
        ResponseHeaderHelper.setHeaders(response);
        return ViewHelper.render(model, "error.mustache");
    }
}
