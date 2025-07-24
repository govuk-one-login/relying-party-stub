package uk.gov.di.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.claims.ClaimRequirement;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSetRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import uk.gov.di.config.Configuration;
import uk.gov.di.config.RPConfig;
import uk.gov.di.utils.Oidc;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuthorizeHandler implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizeHandler.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object handle(Request request, Response response) {
        var relyingPartyConfig =
                Configuration.getRelyingPartyConfig(request.cookie("relyingParty"));
        var oidcClient = new Oidc(relyingPartyConfig);
        try {
            List<String> scopes = new ArrayList<>();
            scopes.add("openid");

            List<NameValuePair> pairs =
                    URLEncodedUtils.parse(request.body(), Charset.defaultCharset());

            Map<String, String> formParameters =
                    pairs.stream()
                            .collect(
                                    Collectors.toMap(
                                            NameValuePair::getName, NameValuePair::getValue));

            String language = formParameters.get("lng");

            if (relyingPartyConfig.clientType().equals("app")) {
                LOG.info("Doc Checking App journey initialized");
                scopes.add("doc-checking-app");
                var opURL =
                        oidcClient.buildDocAppAuthorizeRequest(
                                relyingPartyConfig.authCallbackUrl(),
                                Scope.parse(scopes),
                                language);
                LOG.info("Redirecting to OP");
                response.redirect(opURL);
                return null;
            }

            if (formParameters.containsKey("scopes-email")) {
                LOG.info("Email scope requested");
                scopes.add(formParameters.get("scopes-email"));
            }

            if (formParameters.containsKey("scopes-phone")) {
                LOG.info("Phone scope requested");
                scopes.add(formParameters.get("scopes-phone"));
            }

            if (formParameters.containsKey("scopes-wallet-subject-id")) {
                LOG.info("Wallet Subject ID scope requested");
                scopes.add(formParameters.get("scopes-wallet-subject-id"));
            }

            if (formParameters.containsKey("scopes-account-management")) {
                LOG.info("Account Management scope requested");
                scopes.add(formParameters.get("scopes-account-management"));
            }

            String secondFactorAuthentication = formParameters.get("2fa");

            var prompt = formParameters.get("prompt");

            String rpSid = formParameters.get("rp-sid");

            String idToken = formParameters.get("reauth-id-token");

            String maxAge = formParameters.get("max-age");

            List<String> vtr = new ArrayList<>();

            if (formParameters.containsKey("loc-P0")) {
                var vtrToAdd =
                        "%s.%s".formatted(formParameters.get("loc-P0"), secondFactorAuthentication);
                vtr.add(vtrToAdd);
                LOG.info("VTR value selected: {}", vtrToAdd);
            }

            if (formParameters.containsKey("loc-P1")) {
                var vtrToAdd =
                        "%s.%s".formatted(formParameters.get("loc-P1"), secondFactorAuthentication);
                vtr.add(vtrToAdd);
                LOG.info("VTR value selected: {}", vtrToAdd);
            }

            if (formParameters.containsKey("loc-P2")) {
                var vtrToAdd =
                        "%s.%s".formatted(formParameters.get("loc-P2"), secondFactorAuthentication);
                vtr.add(vtrToAdd);
                LOG.info("VTR value selected: {}", vtrToAdd);
            }

            if (formParameters.containsKey("loc-P3")) {
                var vtrToAdd =
                        "%s.%s".formatted(formParameters.get("loc-P3"), secondFactorAuthentication);
                vtr.add(vtrToAdd);
                LOG.info("VTR value selected: {}", vtrToAdd);
            }

            if (vtr.isEmpty()) {
                vtr.add(secondFactorAuthentication);
            }

            var claimsSetRequest = new ClaimsSetRequest();

            if (formParameters.containsKey("claims-core-identity")) {
                LOG.info("Core Identity claim requested");
                var identityEntry =
                        new ClaimsSetRequest.Entry(formParameters.get("claims-core-identity"))
                                .withClaimRequirement(ClaimRequirement.ESSENTIAL);
                claimsSetRequest = claimsSetRequest.add(identityEntry);
            }

            if (formParameters.containsKey("claims-passport")) {
                LOG.info("Passport claim requested");
                var passportEntry =
                        new ClaimsSetRequest.Entry(formParameters.get("claims-passport"))
                                .withClaimRequirement(ClaimRequirement.ESSENTIAL);
                claimsSetRequest = claimsSetRequest.add(passportEntry);
            }

            if (formParameters.containsKey("claims-address")) {
                LOG.info("Address claim requested");
                var addressEntry =
                        new ClaimsSetRequest.Entry(formParameters.get("claims-address"))
                                .withClaimRequirement(ClaimRequirement.ESSENTIAL);
                claimsSetRequest = claimsSetRequest.add(addressEntry);
            }

            if (formParameters.containsKey("claims-driving-permit")) {
                LOG.info("Driving permit claim requested");
                var drivingPermitEntry =
                        new ClaimsSetRequest.Entry(formParameters.get("claims-driving-permit"))
                                .withClaimRequirement(ClaimRequirement.ESSENTIAL);
                claimsSetRequest = claimsSetRequest.add(drivingPermitEntry);
            }

            if (formParameters.containsKey("claims-return-code")) {
                LOG.info("Return code claim requested");
                var returnCodeEntry =
                        new ClaimsSetRequest.Entry(formParameters.get("claims-return-code"))
                                .withClaimRequirement(ClaimRequirement.ESSENTIAL);
                claimsSetRequest = claimsSetRequest.add(returnCodeEntry);
            }

            if (formParameters.containsKey("claims-inherited-identity")) {
                if (!formParameters.get("claims-inherited-identity").trim().isEmpty()) {
                    LOG.info("Inherited Identity record claim requested");
                    var inheritedIdentity =
                            convertJsonToMap(
                                    "{" + formParameters.get("claims-inherited-identity") + "}");

                    JWTClaimsSet claims =
                            new JWTClaimsSet.Builder()
                                    .subject(
                                            "urn:fdc:gov.uk:2022:-WaWGynBDXunAih77MAGjvYfJfcN9y_wzmuX4MT9MuA")
                                    .audience(Configuration.getIpvEndpoint())
                                    .issuer(Configuration.getInheritedIdentityJwtIssuer())
                                    .notBeforeTime(new Date())
                                    .claim("vot", formParameters.get("vot"))
                                    .claim("vtm", Configuration.getInheritedIdentityJwtVtm())
                                    .jwtID(
                                            String.format(
                                                    "%s:%s",
                                                    "urn:uuid", UUID.randomUUID().toString()))
                                    .claim("vc", inheritedIdentity)
                                    .build();

                    JWSSigner signer =
                            new ECDSASigner(
                                    getSigningKey(
                                            relyingPartyConfig.inheritedIdentityJwtSigningKey()));
                    SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims);
                    signedJwt.sign(signer);

                    var inheritedIdentityEntry =
                            new ClaimsSetRequest.Entry(
                                            "https://vocab.account.gov.uk/v1/inheritedIdentityJWT")
                                    .withValues(List.of(signedJwt.serialize()));
                    claimsSetRequest = claimsSetRequest.add(inheritedIdentityEntry);
                } else {
                    claimsSetRequest.delete("claims-inherited-identity");
                }
            }

            CodeChallengeMethod codeChallengeMethod = null;
            CodeVerifier codeVerifier = null;

            if (formParameters.containsKey("pkce") && formParameters.get("pkce").equals("yes")) {
                codeChallengeMethod = CodeChallengeMethod.S256;
                codeVerifier = new CodeVerifier();

                response.cookie("/", "codeVerifier", codeVerifier.getValue(), 3600, false, true);
            }

            String loginHint = null;

            if (formParameters.containsKey("login-hint")
                    && "object".equals(formParameters.getOrDefault("request", "query"))) {
                loginHint = formParameters.get("login-hint");
            }

            String channel = null;

            if (!Objects.equals(formParameters.get("channel"), "none")) {
                channel = formParameters.get("channel");
            }

            var authRequest =
                    buildAuthorizeRequest(
                            relyingPartyConfig,
                            oidcClient,
                            formParameters,
                            vtr,
                            scopes,
                            claimsSetRequest,
                            language,
                            prompt,
                            rpSid,
                            idToken,
                            maxAge,
                            codeChallengeMethod,
                            codeVerifier,
                            loginHint,
                            channel);

            LOG.info("Redirecting to OP");
            response.redirect(authRequest.toURI().toString());
            return null;

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Map<String, Object> convertJsonToMap(String json)
            throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private static ECPrivateKey getSigningKey(String key)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        byte[] binaryKey = Base64.getDecoder().decode(key);
        KeyFactory factory = KeyFactory.getInstance("EC");
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(binaryKey);
        return (ECPrivateKey) factory.generatePrivate(privateKeySpec);
    }

    private AuthenticationRequest buildAuthorizeRequest(
            RPConfig relyingPartyConfig,
            Oidc oidcClient,
            Map<String, String> formParameters,
            List<String> vtr,
            List<String> scopes,
            ClaimsSetRequest claimsSetRequest,
            String language,
            String prompt,
            String rpSid,
            String idToken,
            String maxAge,
            CodeChallengeMethod codeChallengeMethod,
            CodeVerifier codeVerifier,
            String loginHint,
            String channel)
            throws URISyntaxException {
        if ("object".equals(formParameters.getOrDefault("request", "query"))) {
            LOG.info("Building authorize request with JAR");
            return oidcClient.buildJarAuthorizeRequest(
                    relyingPartyConfig.authCallbackUrl(),
                    vtr,
                    scopes,
                    claimsSetRequest,
                    language,
                    prompt,
                    rpSid,
                    idToken,
                    maxAge,
                    codeChallengeMethod,
                    codeVerifier,
                    loginHint,
                    channel);
        } else {
            LOG.info("Building authorize request with query params");
            return oidcClient.buildQueryParamAuthorizeRequest(
                    relyingPartyConfig.authCallbackUrl(),
                    vtr,
                    scopes,
                    claimsSetRequest,
                    language,
                    prompt,
                    rpSid,
                    maxAge,
                    codeChallengeMethod,
                    codeVerifier,
                    channel);
        }
    }
}
