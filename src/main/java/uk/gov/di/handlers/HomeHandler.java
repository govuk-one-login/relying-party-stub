package uk.gov.di.handlers;

import spark.Request;
import spark.Response;
import spark.Route;
import uk.gov.di.utils.ViewHelper;

import java.util.HashMap;

import static uk.gov.di.config.RelyingPartyConfig.SERVICE_NAME;

public class HomeHandler implements Route {
    @Override
    public Object handle(Request request, Response response) {
        request.session(true);

        var model = new HashMap<>();
        model.put("servicename", SERVICE_NAME);
        return ViewHelper.render(model, "home.mustache");
    }
}