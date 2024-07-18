package uk.gov.di.config;

import uk.gov.di.utils.PrivateKeyReader;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public record RPConfig(
        String clientPrivateKey,
        String identitySigningKeyUrl,
        String accountManagementUrl,
        String clientId,
        String clientType,
        String idTokenSigningAlgorithm,
        String serviceName,
        String opBaseUrl,
        String tokenClientSecret,
        String inheritedIdentityJwtSigningKey) {

    private static final String kid = UUID.randomUUID().toString();

    public String authCallbackUrl() {
        return Configuration.getStubUrl() + "/oidc/authorization-code/callback";
    }

    public String postLogoutRedirectUrl() {
        return Configuration.getStubUrl() + "/signed-out";
    }

    public Map<String, Serializable> jwksConfiguration() {
        return Map.of(
                "client_id",
                clientId(),
                "public_key",
                new PrivateKeyReader(clientPrivateKey()).getPublicKey(),
                "public_key_id",
                kid);
    }
}
