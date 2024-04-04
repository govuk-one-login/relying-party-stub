package uk.gov.di.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import uk.gov.di.config.Configuration;
import uk.gov.di.utils.CoreIdentityValidator;
import uk.gov.di.utils.Oidc;
import uk.gov.di.utils.ViewHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class AuthCallbackHandler implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(AuthCallbackHandler.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        LOG.info("Callback received");
        if (request.queryParams("error") != null) {
            return renderError(request);
        }

        var relyingPartyConfig =
                Configuration.getRelyingPartyConfig(request.cookie("relyingParty"));
        var oidcClient = new Oidc(relyingPartyConfig);
        var validator = CoreIdentityValidator.createValidator(relyingPartyConfig);
        var tokens =
                oidcClient.makeTokenRequest(
                        request.queryParams("code"), relyingPartyConfig.authCallbackUrl());
        oidcClient.validateIdToken(tokens.getIDToken());
        response.cookie("/", "idToken", tokens.getIDToken().getParsedString(), 3600, false, true);
        var userInfo = oidcClient.makeUserInfoRequest(tokens.getAccessToken());

        var model = new HashMap<>();
        model.put("id_token", tokens.getIDToken().getParsedString());
        model.put("access_token", tokens.getAccessToken().toJSONString());
        model.put("user_info_response", userInfo.toJSONString());

        var templateName = "userinfo.mustache";
        if (relyingPartyConfig.clientType().equals("app")) {
            List<String> docAppCredential = (List<String>) userInfo.getClaim("doc-app-credential");
            model.put("doc_app_credential", docAppCredential.get(0));
            templateName = "doc-app-userinfo.mustache";
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
            boolean socialSecurityRecordClaimPresent =
                    Objects.nonNull(
                            userInfo.getClaim(
                                    "https://vocab.account.gov.uk/v1/socialSecurityRecord"));
            model.put("address_claim_present", addressClaimPresent);
            model.put("passport_claim_present", passportClaimPresent);
            model.put("driving_permit_claim_present", drivingPermitClaimPresent);
            model.put("social_security_record_claim_present", socialSecurityRecordClaimPresent);
            model.put("locale_claim", userInfo.getClaim("locale"));
        }
        model.put("my_account_url", relyingPartyConfig.accountManagementUrl());

        return ViewHelper.render(model, templateName);
    }

    private static Object renderError(Request request) {
        LOG.error("Error response in callback");
        var model = new HashMap<>();
        model.put("error", request.queryParams("error"));
        model.put("error_description", request.queryParams("error_description"));
        return ViewHelper.render(model, "callback-error.mustache");
    }
}
