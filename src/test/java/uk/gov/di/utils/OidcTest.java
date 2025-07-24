package uk.gov.di.utils;

import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.OIDCClaimsRequest;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSetRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.di.config.RPConfig;

import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.di.helpers.keyHelper.generateRsaKeyPair;

public class OidcTest {
    private RPConfig rpConfig = mock(RPConfig.class);
    private final KeyPair TEST_RSA_KEY_PAIR = generateRsaKeyPair();
    private final RSAPrivateKey testPrivateKey = (RSAPrivateKey) TEST_RSA_KEY_PAIR.getPrivate();
    private final String serializedPrivateKey =
            Base64.getMimeEncoder().encodeToString(testPrivateKey.getEncoded());
    private final String testClientId = "aClient";
    private final String testCallbackUri = "https://example.com/authentication-callback";
    private final List<String> testVtr = List.of("Cl.Cm");
    private final List<String> testScopes = List.of("openid", "phone");
    private final ClaimsSetRequest testClaimSetRequest =
            new ClaimsSetRequest()
                    .add(new ClaimsSetRequest.Entry("https://vocab.example.com/v1/permit"));

    private final String mockIdpConfig =
            ("""
            {"authorization_endpoint":"http://localhost:8080/authorize","token_endpoint":"http://localhost:8080/token","registration_endpoint":"http://localhost:8080/connect/register","issuer":"http://localhost:8080","jwks_uri":"http://localhost:8080/.well-known/jwks.json","scopes_supported":["openid","email","phone","offline_access"],"response_types_supported":["code"],"grant_types_supported":["authorization_code"],"code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["private_key_jwt","client_secret_post"],"token_endpoint_auth_signing_alg_values_supported":["RS256","RS384","RS512","PS256","PS384","PS512"],"ui_locales_supported":["en","cy"],"service_documentation":"https://docs.sign-in.service.gov.uk/","op_policy_uri":"http://localhost:8081/privacy-notice","op_tos_uri":"http://localhost:8081/terms-and-conditions","request_parameter_supported":true,"trustmarks":"http://localhost:8080/trustmark","subject_types_supported":["public","pairwise"],"userinfo_endpoint":"http://localhost:8080/userinfo","end_session_endpoint":"http://localhost:8080/logout","id_token_signing_alg_values_supported":["ES256"],"claim_types_supported":["normal"],"claims_supported":["sub","email","email_verified","phone_number","phone_number_verified","wallet_subject_id","https://vocab.account.gov.uk/v1/passport","https://vocab.account.gov.uk/v1/drivingPermit","https://vocab.account.gov.uk/v1/coreIdentityJWT","https://vocab.account.gov.uk/v1/address","https://vocab.account.gov.uk/v1/inheritedIdentityJWT","https://vocab.account.gov.uk/v1/returnCode"],"request_uri_parameter_supported":false,"backchannel_logout_supported":true,"backchannel_logout_session_supported":false}
            """);
    private Oidc oidc;

    @BeforeEach
    void setup() throws com.nimbusds.oauth2.sdk.ParseException {
        when(rpConfig.clientPrivateKey()).thenReturn(serializedPrivateKey);
        when(rpConfig.clientId()).thenReturn(testClientId);
        when(rpConfig.jwksConfiguration()).thenReturn(Map.of("public_key_id", "12345"));
        when(rpConfig.opBaseUrl()).thenReturn("https:/example.com/idp");
        var parsedIdpConfig = OIDCProviderMetadata.parse(mockIdpConfig);

        try (MockedStatic<OIDCProviderMetadata> metaDataClass =
                Mockito.mockStatic(OIDCProviderMetadata.class)) {
            metaDataClass
                    .when(() -> OIDCProviderMetadata.resolve(any()))
                    .thenReturn(parsedIdpConfig);
            oidc = new Oidc(rpConfig);
        }
    }

    @Test
    void shouldCreateJARWithExpectedUserInfoClaimsWhenNoPkceAttributesProvided()
            throws ParseException {
        var authorizeRequest =
                oidc.buildJarAuthorizeRequest(
                        testCallbackUri,
                        testVtr,
                        testScopes,
                        testClaimSetRequest,
                        "en",
                        "login",
                        "",
                        "",
                        "123",
                        null,
                        null,
                        null,
                        "web");

        var jarClaims = authorizeRequest.getRequestObject().getJWTClaimsSet();
        var expectedClaims = new OIDCClaimsRequest().withUserInfoClaimsRequest(testClaimSetRequest);
        assertEquals(expectedClaims.toJSONString(), jarClaims.getStringClaim("claims"));
        assertNull(jarClaims.getClaim("id_token_hint"));
        assertNull(jarClaims.getClaim("rp_sid"));

        assertNull(jarClaims.getClaim("code_challenge"));
        assertNull(jarClaims.getClaim("code_challenge_method"));
    }

    @Test
    void shouldCreateJARWithExpectedUserInfoClaimsWhenPkceAttributesProvided()
            throws ParseException {
        var codeChallengeMethod = CodeChallengeMethod.S256;
        var codeVerifier = new CodeVerifier();

        var authorizeRequest =
                oidc.buildJarAuthorizeRequest(
                        testCallbackUri,
                        testVtr,
                        testScopes,
                        testClaimSetRequest,
                        "en",
                        "login",
                        "",
                        "",
                        "123",
                        codeChallengeMethod,
                        codeVerifier,
                        null,
                        "web");

        var jarClaims = authorizeRequest.getRequestObject().getJWTClaimsSet();
        var expectedClaims = new OIDCClaimsRequest().withUserInfoClaimsRequest(testClaimSetRequest);
        assertEquals(expectedClaims.toJSONString(), jarClaims.getStringClaim("claims"));
        assertNull(jarClaims.getClaim("id_token_hint"));
        assertNull(jarClaims.getClaim("rp_sid"));

        var expectedCodeChallengeClaim =
                CodeChallenge.compute(codeChallengeMethod, codeVerifier).getValue();

        assertEquals(expectedCodeChallengeClaim, jarClaims.getClaim("code_challenge"));
        assertEquals(codeChallengeMethod.getValue(), jarClaims.getClaim("code_challenge_method"));
    }

    @Test
    void
            shouldCreateQueryParamAuthorizeRequestWithExpectedUserInfoClaimsWhenNoPkceAttributesProvided()
                    throws ParseException, URISyntaxException {
        var authorizeRequest =
                oidc.buildQueryParamAuthorizeRequest(
                        testCallbackUri,
                        testVtr,
                        testScopes,
                        testClaimSetRequest,
                        "en",
                        "login",
                        "",
                        "123",
                        null,
                        null,
                        "web");

        var jarClaims = authorizeRequest.toJWTClaimsSet();
        var expectedClaims = new OIDCClaimsRequest().withUserInfoClaimsRequest(testClaimSetRequest);
        assertEquals(expectedClaims.toJSONString(), jarClaims.getStringClaim("claims"));
        assertNull(jarClaims.getClaim("id_token_hint"));
        assertNull(jarClaims.getClaim("rp_sid"));

        assertNull(jarClaims.getClaim("code_challenge"));
        assertNull(jarClaims.getClaim("code_challenge_method"));
    }

    @Test
    void
            shouldCreateQueryParamAuthorizeRequestWithExpectedUserInfoClaimsWhenPkceAttributesProvided()
                    throws ParseException, URISyntaxException {
        var codeChallengeMethod = CodeChallengeMethod.S256;
        var codeVerifier = new CodeVerifier();

        var authorizeRequest =
                oidc.buildQueryParamAuthorizeRequest(
                        testCallbackUri,
                        testVtr,
                        testScopes,
                        testClaimSetRequest,
                        "en",
                        "login",
                        "",
                        "123",
                        codeChallengeMethod,
                        codeVerifier,
                        "web");

        var jarClaims = authorizeRequest.toJWTClaimsSet();
        var expectedClaims = new OIDCClaimsRequest().withUserInfoClaimsRequest(testClaimSetRequest);
        assertEquals(expectedClaims.toJSONString(), jarClaims.getStringClaim("claims"));
        assertNull(jarClaims.getClaim("id_token_hint"));
        assertNull(jarClaims.getClaim("rp_sid"));

        var expectedCodeChallengeClaim =
                CodeChallenge.compute(codeChallengeMethod, codeVerifier).getValue();

        assertEquals(expectedCodeChallengeClaim, jarClaims.getClaim("code_challenge"));
        assertEquals(codeChallengeMethod.getValue(), jarClaims.getClaim("code_challenge_method"));
    }
}
