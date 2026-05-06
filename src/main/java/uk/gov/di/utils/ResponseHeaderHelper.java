package uk.gov.di.utils;

import io.javalin.http.Context;

public class ResponseHeaderHelper {
    private ResponseHeaderHelper() {
        throw new IllegalStateException("Utility Class");
    }

    public static void setHeaders(Context ctx) {
        ctx.header("Server", "govuk-sign-in-stub-rp");
        ctx.header("Content-Security-Policy", "default-src 'self'");
        ctx.header("X-Frame-Options", "DENY");
        ctx.header("X-XSS-Protection", "0");
        ctx.header("X-Content-Type-Options", "nosniff");
    }
}
