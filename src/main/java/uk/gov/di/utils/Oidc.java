package uk.gov.di.utils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.langtag.LangTag;
import com.nimbusds.langtag.LangTagException;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretPost;
import com.nimbusds.oauth2.sdk.auth.PrivateKeyJWT;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.Tokens;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCClaimsRequest;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.Prompt;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSetRequest;
import com.nimbusds.openid.connect.sdk.claims.LogoutTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import com.nimbusds.openid.connect.sdk.validators.LogoutTokenValidator;
import net.minidev.json.JSONArray;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.config.RPConfig;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Oidc {

    private static final Logger LOG = LoggerFactory.getLogger(Oidc.class);
    private final RPConfig relyingPartyConfig;

    private final OIDCProviderMetadata providerMetadata;
    private final String idpUrl;
    private final ClientID clientId;
    private final PrivateKeyReader privateKeyReader;

    public Oidc(RPConfig relyingPartyConfig) {
        this.relyingPartyConfig = relyingPartyConfig;
        this.idpUrl = relyingPartyConfig.opBaseUrl();
        this.clientId = new ClientID(relyingPartyConfig.clientId());
        this.providerMetadata = loadProviderMetadata(idpUrl);
        this.privateKeyReader = new PrivateKeyReader(relyingPartyConfig.clientPrivateKey());
    }

    private OIDCProviderMetadata loadProviderMetadata(String baseUrl) {
        try {
            return OIDCProviderMetadata.resolve(new Issuer(baseUrl));
        } catch (Exception e) {
            LOG.error("Unexpected exception thrown when loading provider metadata", e);
            throw new RuntimeException(e);
        }
    }

    public UserInfo makeUserInfoRequest(AccessToken accessToken)
            throws IOException, ParseException {
        LOG.info("Making userinfo request");
        var httpResponse =
                new UserInfoRequest(
                                this.providerMetadata.getUserInfoEndpointURI(),
                                new BearerAccessToken(accessToken.toString()))
                        .toHTTPRequest()
                        .send();

        var userInfoResponse = UserInfoResponse.parse(httpResponse);

        if (!userInfoResponse.indicatesSuccess()) {
            LOG.error("Userinfo request was unsuccessful");
            throw new RuntimeException(userInfoResponse.toErrorResponse().toString());
        }

        LOG.info("Userinfo request was successful");

        return userInfoResponse.toSuccessResponse().getUserInfo();
    }

    public OIDCTokens makeTokenRequest(
            String authCode, String authCallbackUrl, String codeVerifierValue)
            throws URISyntaxException {
        LOG.info("Making Token Request");

        var codeVerifier = (codeVerifierValue != null) ? new CodeVerifier(codeVerifierValue) : null;

        var codeGrant =
                new AuthorizationCodeGrant(
                        new AuthorizationCode(authCode), new URI(authCallbackUrl), codeVerifier);

        try {
            var clientAuthentication =
                    Optional.ofNullable(relyingPartyConfig.tokenClientSecret())
                            .map(this::clientSecretPost)
                            .orElseGet(this::privateKeyJwt);

            var request =
                    new TokenRequest(
                            this.providerMetadata.getTokenEndpointURI(),
                            clientAuthentication,
                            codeGrant,
                            null,
                            null,
                            null);

            var tokenResponse = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());

            if (!tokenResponse.indicatesSuccess()) {
                LOG.error("TokenRequest was unsuccessful");
                throw new RuntimeException(
                        tokenResponse.toErrorResponse().getErrorObject().toString());
            }

            LOG.info("TokenRequest was successful");

            Optional.of(tokenResponse)
                    .map(TokenResponse::toSuccessResponse)
                    .map(AccessTokenResponse::getTokens)
                    .map(Tokens::getAccessToken)
                    .map(AccessToken::getLifetime)
                    .ifPresentOrElse(
                            lifetime -> LOG.info("Access token expires in {}", lifetime),
                            () -> LOG.warn("No expiry on access token"));

            return tokenResponse.toSuccessResponse().getTokens().toOIDCTokens();

        } catch (ParseException | IOException e) {
            LOG.error("Unexpected exception thrown when making token request", e);
            throw new RuntimeException(e);
        }
    }

    private ClientAuthentication clientSecretPost(String secret) {
        return new ClientSecretPost(new ClientID(this.clientId), new Secret(secret));
    }

    private ClientAuthentication privateKeyJwt() {
        var localDateTime = LocalDateTime.now().plusMinutes(5);
        var expiryDate = Date.from(localDateTime.atZone(ZoneId.of("UTC")).toInstant());

        var claims =
                new JWTClaimsSet.Builder()
                        .subject(this.clientId.getValue())
                        .issuer(this.clientId.getValue())
                        .audience(this.providerMetadata.getTokenEndpointURI().toString())
                        .expirationTime(expiryDate)
                        .claim("client_id", this.clientId)
                        .build();

        return new PrivateKeyJWT(signJwtWithClaims(claims));
    }

    public AuthenticationRequest buildJarAuthorizeRequest(
            String callbackUrl,
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
            throws RuntimeException {
        LOG.info("Building JAR Authorize Request");
        Prompt authRequestPrompt;
        try {
            authRequestPrompt = Prompt.parse(prompt);
        } catch (ParseException e) {
            throw new RuntimeException("Unable to parse prompt", e);
        }

        var userInfoClaimsRequest =
                new OIDCClaimsRequest().withUserInfoClaimsRequest(claimsSetRequest);
        var requestObject =
                new JWTClaimsSet.Builder()
                        .audience(this.providerMetadata.getAuthorizationEndpointURI().toString())
                        .claim("redirect_uri", callbackUrl)
                        .claim("response_type", ResponseType.CODE.toString())
                        .claim("scope", Scope.parse(scopes).toString())
                        .claim("nonce", new Nonce().getValue())
                        .claim("client_id", this.clientId.getValue())
                        .claim("state", new State().getValue())
                        .claim("vtr", vtr)
                        .claim("claims", userInfoClaimsRequest.toJSONString())
                        .claim("prompt", authRequestPrompt.toString())
                        .issuer(this.clientId.getValue());

        if (!language.isBlank()) {
            try {
                LOG.info("Adding ui_locales to Authorize Request {}", language);
                requestObject.claim("ui_locales", List.of(LangTag.parse(language)));
            } catch (LangTagException e) {
                LOG.error("Unable to parse language {}", language);
            }
        }

        if (rpSid != null && !rpSid.isBlank()) {
            requestObject.claim("rp_sid", rpSid);
        }

        if (idToken != null && !idToken.isBlank()) {
            requestObject.claim("id_token_hint", idToken);
        }

        if (maxAge != null && !maxAge.isBlank()) {
            requestObject.claim("max_age", maxAge);
        }

        if (Objects.nonNull(codeVerifier)) {
            validateCodeChallengeMethodNotNull(codeChallengeMethod);

            requestObject.claim("code_challenge_method", codeChallengeMethod.getValue());

            var codeChallenge = CodeChallenge.compute(codeChallengeMethod, codeVerifier);
            requestObject.claim("code_challenge", codeChallenge.getValue());
        }

        if (loginHint != null && !loginHint.isBlank()) {
            requestObject.claim("login_hint", loginHint);
        }

        if (channel != null) {
            requestObject.claim("channel", channel);
        }

        return new AuthenticationRequest.Builder(
                        ResponseType.CODE, Scope.parse(scopes), this.clientId, null)
                .endpointURI(this.providerMetadata.getAuthorizationEndpointURI())
                .requestObject(signJwtWithClaims(requestObject.build()))
                .build();
    }

    public AuthenticationRequest buildQueryParamAuthorizeRequest(
            String callbackUrl,
            List<String> vtr,
            List<String> scopes,
            ClaimsSetRequest claimsSetRequest,
            String language,
            String prompt,
            String rpSid,
            String maxAge,
            CodeChallengeMethod codeChallengeMethod,
            CodeVerifier codeVerifier,
            String channel)
            throws URISyntaxException, RuntimeException {
        LOG.info("Building Authorize Request");
        Prompt authRequestPrompt;
        try {
            authRequestPrompt = Prompt.parse(prompt);
        } catch (ParseException e) {
            throw new RuntimeException("Unable to parse prompt", e);
        }

        var authorizationRequestBuilder =
                new AuthenticationRequest.Builder(
                                new ResponseType(ResponseType.Value.CODE),
                                Scope.parse(scopes),
                                this.clientId,
                                new URI(callbackUrl))
                        .state(new State())
                        .nonce(new Nonce())
                        .prompt(authRequestPrompt)
                        .endpointURI(this.providerMetadata.getAuthorizationEndpointURI())
                        .customParameter("vtr", JSONArray.toJSONString(vtr));

        if (Objects.nonNull(codeVerifier)) {
            validateCodeChallengeMethodNotNull(codeChallengeMethod);

            authorizationRequestBuilder.codeChallenge(codeVerifier, codeChallengeMethod);
        }

        if (claimsSetRequest.getEntries().size() > 0) {
            LOG.info("Adding claims to Authorize Request");
            authorizationRequestBuilder.claims(
                    new OIDCClaimsRequest().withUserInfoClaimsRequest(claimsSetRequest));
        }

        if (!language.isBlank()) {
            try {
                LOG.info("Adding ui_locales to Authorize Request {}", language);
                authorizationRequestBuilder.uiLocales(List.of(LangTag.parse(language)));
            } catch (LangTagException e) {
                LOG.error("Unable to parse language {}", language);
            }
        }

        if (rpSid != null && !rpSid.isBlank()) {
            authorizationRequestBuilder.customParameter("rp_sid", rpSid);
        }

        if (maxAge != null && !maxAge.isBlank()) {
            authorizationRequestBuilder.maxAge(Integer.parseInt(maxAge));
        }

        if (channel != null) {
            authorizationRequestBuilder.customParameter("channel", channel);
        }

        return authorizationRequestBuilder.build();
    }

    public String getAuthorizationEndpoint() {
        return this.providerMetadata.getAuthorizationEndpointURI().toString();
    }

    public String buildDocAppAuthorizeRequest(String callbackUrl, Scope scopes, String language) {
        LOG.info("Building secure Authorize Request");
        var authRequestBuilder =
                new AuthorizationRequest.Builder(
                                new ResponseType(ResponseType.Value.CODE), this.clientId)
                        .requestObject(generateSignedJWT(scopes, callbackUrl, language))
                        .scope(new Scope(OIDCScopeValue.OPENID))
                        .endpointURI(this.providerMetadata.getAuthorizationEndpointURI());

        return authRequestBuilder.build().toURI().toString();
    }

    public String buildLogoutUrl(String idToken, String state, String postLogoutRedirectUri)
            throws URISyntaxException {
        var logoutUri =
                new URIBuilder(this.idpUrl + (this.idpUrl.endsWith("/") ? "logout" : "/logout"));
        logoutUri.addParameter("id_token_hint", idToken);
        logoutUri.addParameter("state", state);
        logoutUri.addParameter("post_logout_redirect_uri", postLogoutRedirectUri);

        return logoutUri.build().toString();
    }

    public void validateIdToken(JWT idToken) throws MalformedURLException {
        LOG.info("Validating ID token");
        ResourceRetriever resourceRetriever = new DefaultResourceRetriever(30000, 30000);
        var idTokenValidator =
                new IDTokenValidator(
                        this.providerMetadata.getIssuer(),
                        this.clientId,
                        JWSAlgorithm.parse(this.relyingPartyConfig.idTokenSigningAlgorithm()),
                        this.providerMetadata.getJWKSetURI().toURL(),
                        resourceRetriever);

        try {
            idTokenValidator.validate(idToken, null);
        } catch (BadJOSEException | JOSEException e) {
            LOG.error("Unexpected exception thrown when validating ID token", e);
            throw new RuntimeException(e);
        }
    }

    public Optional<LogoutTokenClaimsSet> validateLogoutToken(JWT logoutToken) {
        try {
            var validator =
                    new LogoutTokenValidator(
                            this.providerMetadata.getIssuer(),
                            this.clientId,
                            JWSAlgorithm.parse(relyingPartyConfig.idTokenSigningAlgorithm()),
                            this.providerMetadata.getJWKSetURI().toURL(),
                            new DefaultResourceRetriever(30000, 30000));

            return Optional.of(validator.validate(logoutToken));
        } catch (BadJOSEException | JOSEException | MalformedURLException e) {
            LOG.error("Unexpected exception thrown when validating logout token", e);
            return Optional.empty();
        }
    }

    private void validateCodeChallengeMethodNotNull(CodeChallengeMethod codeChallengeMethod)
            throws RuntimeException {
        if (Objects.isNull(codeChallengeMethod)) {
            LOG.error("Code challenge method not set when code verifier has been provided.");
            throw new RuntimeException("Code challenge method not set.");
        }
    }

    private SignedJWT generateSignedJWT(Scope scopes, String callbackURL, String language) {
        var jwtClaimsSet =
                new JWTClaimsSet.Builder()
                        .audience(this.providerMetadata.getAuthorizationEndpointURI().toString())
                        .subject(new Subject().getValue())
                        .claim("redirect_uri", callbackURL)
                        .claim("response_type", ResponseType.CODE.toString())
                        .claim("scope", scopes.toString())
                        .claim("nonce", new Nonce().getValue())
                        .claim("client_id", this.clientId.getValue())
                        .claim("state", new State().getValue())
                        .claim("ui_locales", language)
                        .issuer(this.clientId.getValue())
                        .build();
        return signJwtWithClaims(jwtClaimsSet);
    }

    private SignedJWT signJwtWithClaims(JWTClaimsSet jwtClaimsSet) {
        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.RS512)
                        .keyID(
                                relyingPartyConfig
                                        .jwksConfiguration()
                                        .get("public_key_id")
                                        .toString())
                        .build();

        var signedJWT = new SignedJWT(header, jwtClaimsSet);

        try {
            signedJWT.sign(new RSASSASigner(this.privateKeyReader.get()));
        } catch (JOSEException e) {
            LOG.error("Unable to sign secure request object", e);
            throw new RuntimeException("Unable to sign secure request object", e);
        }

        return signedJWT;
    }
}
