package uk.gov.di.handlers;

import io.javalin.http.Context;
import io.javalin.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.config.Configuration;
import uk.gov.di.config.RPConfig;
import uk.gov.di.utils.CoreIdentityValidator;
import uk.gov.di.utils.Oidc;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AuthCallbackHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AuthCallbackHandler.class);

    public void handle(Context ctx) throws Exception {
        LOG.info("Callback received");
        if (ctx.queryParam("error") != null) {
            renderError(ctx);
            return;
        }

        var relyingPartyConfig = Configuration.getRelyingPartyConfig(ctx.cookie("relyingParty"));
        var oidcClient = new Oidc(relyingPartyConfig);
        var validator = CoreIdentityValidator.createValidator(relyingPartyConfig);

        var codeVerifierValue = ctx.cookie("codeVerifier");
        if (codeVerifierValue != null) {
            ctx.removeCookie("codeVerifier", "/");
        }
        var useAlternativeDomain = "true".equals(ctx.cookie("useAlternativeDomain"));
        var tokens =
                oidcClient.makeTokenRequest(
                        ctx.queryParam("code"),
                        relyingPartyConfig.authCallbackUrl(),
                        codeVerifierValue,
                        useAlternativeDomain);
        oidcClient.validateIdToken(tokens.getIDToken(), useAlternativeDomain);
        ctx.cookie(
                new Cookie(
                        "idToken", tokens.getIDToken().getParsedString(), "/", 3600, false, true));
        var userInfo =
                oidcClient.makeUserInfoRequest(tokens.getAccessToken(), useAlternativeDomain);

        var model = new HashMap<String, Object>();
        model.put("id_token", tokens.getIDToken().getParsedString());
        model.put("access_token", tokens.getAccessToken().toJSONString());
        model.put("user_info_response", userInfo.toJSONString());
        model.put("journey_id", tokens.getIDToken().getJWTClaimsSet().getStringClaim("sid"));
        model.put("client_name", relyingPartyConfig.serviceName());

        var templateName = "/userinfo.mustache";
        if (relyingPartyConfig.clientType().equals("app")) {
            List<String> docAppCredential = (List<String>) userInfo.getClaim("doc-app-credential");
            model.put("doc_app_credential", docAppCredential.get(0));
            templateName = "/doc-app-userinfo.mustache";
        } else {
            model.put("email", userInfo.getEmailAddress());
            model.put("phone_number", userInfo.getPhoneNumber());

            var walletSubjectID = userInfo.getClaim("wallet_subject_id");
            boolean walletSubjectIDPresent = Objects.nonNull(walletSubjectID);
            model.put("wallet_subject_id_present", walletSubjectIDPresent);
            model.put("wallet_subject_id", walletSubjectID);

            var coreIdentityJWT =
                    userInfo.getStringClaim("https://vocab.account.gov.uk/v1/coreIdentityJWT");
            boolean coreIdentityClaimPresent = Objects.nonNull(coreIdentityJWT);
            model.put("core_identity_claim_present", coreIdentityClaimPresent);
            model.put("core_identity_claim", coreIdentityJWT);
            if (coreIdentityClaimPresent) {
                model.put("core_identity_claim_signature", validator.isValid(coreIdentityJWT));
            }

            var returnCodeClaim = userInfo.getClaim("https://vocab.account.gov.uk/v1/returnCode");
            boolean returnCodeClaimPresent = Objects.nonNull(returnCodeClaim);
            model.put("return_code_claim_present", returnCodeClaimPresent);
            model.put("return_code_claim", returnCodeClaim);

            boolean addressClaimPresent =
                    Objects.nonNull(userInfo.getClaim("https://vocab.account.gov.uk/v1/address"));
            boolean passportClaimPresent =
                    Objects.nonNull(userInfo.getClaim("https://vocab.account.gov.uk/v1/passport"));
            boolean drivingPermitClaimPresent =
                    Objects.nonNull(
                            userInfo.getClaim("https://vocab.account.gov.uk/v1/drivingPermit"));
            model.put("address_claim_present", addressClaimPresent);
            model.put("passport_claim_present", passportClaimPresent);
            model.put("driving_permit_claim_present", drivingPermitClaimPresent);
            model.put("locale_claim", userInfo.getClaim("locale"));
        }
        model.put("my_account_url", relyingPartyConfig.accountManagementUrl());

        ctx.render(templateName, model);
    }

    private static void renderError(Context ctx) {
        LOG.error("Error response in callback");
        Optional<RPConfig> rpConfigOptional = Optional.empty();
        try {
            rpConfigOptional =
                    Optional.of(Configuration.getRelyingPartyConfig(ctx.cookie("relyingParty")));
        } catch (Exception e) {
            LOG.warn("Failed to get config for callback error, ignoring: {}", e.getMessage());
        }

        var model = new HashMap<String, Object>();
        model.put("error", ctx.queryParam("error"));
        model.put("error_description", ctx.queryParam("error_description"));

        rpConfigOptional.ifPresent(c -> model.put("client_name", c.serviceName()));
        ctx.render("/callback-error.mustache", model);
    }
}
