package uk.gov.di.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Configuration extends HashMap<String, RPConfig> {

    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);
    private static final String LOCAL_CONFIGURATION_SOURCE = "local";
    private static Configuration instance;

    public static Configuration getInstance() {
        if (instance == null) {
            instance = fetchConfiguration();
        }
        return instance;
    }

    public static RPConfig getRelyingPartyConfig(String relyingPartyName) {
        getInstance();

        if (relyingPartyName == null) {
            relyingPartyName = defaultClientId();
        }

        if (relyingPartyName == null) {
            var defaultConfig =
                    instance.entrySet().stream()
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new MissingConfigurationException(
                                                    "Empty or missing configuration"));
            return defaultConfig.getValue();
        }

        return Optional.ofNullable(instance.get(relyingPartyName))
                .orElseThrow(
                        () ->
                                new MissingConfigurationException(
                                        "Requested RP not present in configuration"));
    }

    public static String getStubUrl() {
        return Optional.ofNullable(System.getenv("STUB_URL"))
                .orElseThrow(() -> new MissingConfigurationException("No stub url configured"));
    }

    public static String getInheritedIdentityJwtIssuer() {
        return "https://orch.stubs.account.gov.uk/migration/v1";
    }

    public static String getInheritedIdentityJwtVtm() {
        return "https://hmrc.gov.uk/trustmark";
    }

    public static String getIpvEndpoint() {
        return "https://identity.build.account.gov.uk/";
    }

    private static Configuration fetchConfiguration() {
        String config;
        if (LOCAL_CONFIGURATION_SOURCE.equals(configSource())) {
            config = fetchLocalConfig();
        } else {
            config = fetchConfigFromSecretsManager();
        }

        var serialiser =
                new GsonBuilder()
                        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create();
        return serialiser.fromJson(config, Configuration.class);
    }

    private static String fetchLocalConfig() {
        var configPath = "config.json";
        String config;
        try (BufferedReader reader = new BufferedReader(new FileReader(configPath))) {
            config = reader.lines().collect(Collectors.joining());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return config;
    }

    private static String fetchConfigFromSecretsManager() {
        String secretName = configSource();
        Region region = Region.of("eu-west-2");
        try (SecretsManagerClient client = SecretsManagerClient.builder().region(region).build()) {
            var secretRequest = GetSecretValueRequest.builder().secretId(secretName).build();

            return client.getSecretValue(secretRequest).secretString();
        }
    }

    private static String configSource() {
        return System.getenv().getOrDefault("CONFIGURATION_SOURCE", LOCAL_CONFIGURATION_SOURCE);
    }

    private static String defaultClientId() {
        return System.getenv().getOrDefault("DEFAULT_CLIENT_ID", null);
    }

    public static Stream<Map<String, Serializable>> getClientsPublicKeys() {
        LOG.info(
                "getClientsPublicKeys: "
                        + getInstance().values().stream().map(RPConfig::jwksConfiguration));
        return getInstance().values().stream().map(RPConfig::jwksConfiguration);
    }
}
