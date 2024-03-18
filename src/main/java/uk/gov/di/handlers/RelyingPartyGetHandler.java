package uk.gov.di.handlers;

import spark.Request;
import spark.Response;
import spark.Route;
import uk.gov.di.config.Configuration;
import uk.gov.di.utils.ViewHelper;

import java.util.HashMap;

public class RelyingPartyGetHandler implements Route {
    @Override
    public Object handle(Request request, Response response) throws Exception {
        var model = new HashMap<>();
        model.put("relyingParties", Configuration.getInstance().values().stream().toList());
        return ViewHelper.render(model, "relying-parties.mustache");
    }
}
