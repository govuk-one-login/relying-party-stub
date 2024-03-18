package uk.gov.di.config;

public record RPConfig(
        String clientPrivateKey,
        String identitySigningPublicKey,
        String accountManagementUrl,
        String clientId,
        String clientType,
        String idTokenSigningAlgorithm,
        String serviceName,
        String opBaseUrl,
        String tokenClientSecret) {
    public String authCallbackUrl() {
        return Configuration.getStubUrl() + "/oidc/authorization-code/callback";
    }

    public String postLogoutRedirectUrl() {
        return Configuration.getStubUrl() + "/signed-out";
    }
}
