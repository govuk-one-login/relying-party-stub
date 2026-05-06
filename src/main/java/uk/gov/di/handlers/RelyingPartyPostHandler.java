package uk.gov.di.handlers;

import io.javalin.http.Context;
import io.javalin.http.Cookie;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RelyingPartyPostHandler {
    public void handle(Context ctx) {
        List<NameValuePair> pairs = URLEncodedUtils.parse(ctx.body(), Charset.defaultCharset());

        Map<String, String> formParameters =
                pairs.stream()
                        .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));

        ctx.cookie(
                new Cookie(
                        "relyingParty",
                        formParameters.get("relying-party"),
                        "/",
                        3600,
                        false,
                        true));
        if ("on".equals(formParameters.get("use-alternative-domain"))) {
            ctx.cookie(new Cookie("useAlternativeDomain", "true", "/", 3600, false, true));
        } else {
            ctx.removeCookie("useAlternativeDomain", "/");
        }
        ctx.redirect("/");
    }
}
