package uk.gov.di.handlers;

import io.javalin.http.Context;
import uk.gov.di.config.Configuration;

import java.util.HashMap;

public class RelyingPartyGetHandler {
    public void handle(Context ctx) {
        var model = new HashMap<String, Object>();
        model.put("relyingParties", Configuration.getInstance().values().stream().toList());
        model.put("useAlternativeDomain", "true".equals(ctx.cookie("useAlternativeDomain")));
        ctx.render("/relying-parties.mustache", model);
    }
}
