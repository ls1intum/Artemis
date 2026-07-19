package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class HyperionSecretMaterialPolicyTest {

    private static final String GITHUB_SENTINEL = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

    private final HyperionSecretMaterialPolicy policy = new HyperionSecretMaterialPolicy();

    @ParameterizedTest
    @ValueSource(strings = { ".env", "config/.ENV.Production", ".npmrc", ".pypirc", ".netrc", ".git-credentials", "config/application_default_credentials.json",
            "service-account.json", ".aws/credentials", ".azure/accessTokens.json", ".config/gcloud/credentials.db", ".docker/config.json", ".kube/config" })
    void assessRejectsCanonicalCredentialPathsCaseInsensitively(String path) {
        assertThat(policy.assess(path, bytes("ordinary"), HyperionSecretMaterialPolicy.Origin.WORKSPACE_ARCHIVE).category())
                .contains(HyperionSecretMaterialPolicy.Category.CREDENTIAL_FILE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "id_rsa", "keys/ID_ED25519", "tls/server.key", "tls/server.p12", "tls/server.pfx", "tls/server.jks", "tls/server.keystore" })
    void assessRejectsPrivateKeyAndKeystoreContainers(String path) {
        assertThat(policy.assess(path, new byte[] { 0, 1, 2 }, HyperionSecretMaterialPolicy.Origin.WORKSPACE_ARCHIVE).category())
                .contains(HyperionSecretMaterialPolicy.Category.PRIVATE_KEY_CONTAINER);
    }

    @ParameterizedTest
    @ValueSource(strings = { "PRIVATE KEY", "ENCRYPTED PRIVATE KEY", "RSA PRIVATE KEY", "EC PRIVATE KEY", "DSA PRIVATE KEY", "OPENSSH PRIVATE KEY", "VENDOR ENCRYPTED PRIVATE KEY",
            "PGP PRIVATE KEY BLOCK" })
    void assessRejectsAllPemPrivateKeyArmor(String label) {
        String content = "-----BEGIN " + label + "-----\nsynthetic-fixture\n-----END " + label + "-----";

        assertThat(policy.assess("src/fixture.txt", bytes(content), HyperionSecretMaterialPolicy.Origin.GENERATED_CANDIDATE).category())
                .contains(HyperionSecretMaterialPolicy.Category.PEM_PRIVATE_KEY);
    }

    @ParameterizedTest
    @MethodSource("supportedProviderTokens")
    void assessRejectsOnlyNamedStructurallyReliableProviderTokens(String content, HyperionSecretMaterialPolicy.Category category) {
        assertThat(policy.assess("src/fixture.txt", bytes("prefix " + content + " suffix"), HyperionSecretMaterialPolicy.Origin.PROVIDER_PROMPT).category()).contains(category);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ghp_", "gho_", "ghu_", "ghs_", "ghr_" })
    void assessRejectsEveryGithubTokenPrefix(String prefix) {
        String token = prefix + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

        assertThat(policy.assess("src/fixture.txt", bytes("prefix " + token + " suffix"), HyperionSecretMaterialPolicy.Origin.PROVIDER_PROMPT).category())
                .contains(HyperionSecretMaterialPolicy.Category.GITHUB_TOKEN);
    }

    private static Stream<Arguments> supportedProviderTokens() {
        return Stream.of(Arguments.of("AKIAIOSFODNN7EXAMPLE", HyperionSecretMaterialPolicy.Category.AWS_ACCESS_KEY_ID),
                Arguments.of(GITHUB_SENTINEL, HyperionSecretMaterialPolicy.Category.GITHUB_TOKEN),
                Arguments.of("glpat-abcdefghijklmnopqrst", HyperionSecretMaterialPolicy.Category.GITLAB_TOKEN));
    }

    @Test
    void assessAllowsOrdinarySourcePlaceholdersAndOpaqueIdentifiers() {
        String ordinarySource = """
                class Example {
                    String token = "token";
                    String password = "change-me";
                    String secret = "example-secret";
                    String apiKey = "your-api-key-here";
                    String uuid = "477444bc-083e-478c-90fd-ce3037063361";
                    String sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
                    String jwtFixture = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmaXh0dXJlIn0.signature";
                    String nearAws = "AKIAIOSFODNN7EXAMPL";
                    String nearGithub = "ghp_short";
                    String nearGitlab = "glpat-exampletokenvalue";
                }
                """;

        assertThat(policy.assess("src/Example.java", bytes(ordinarySource), HyperionSecretMaterialPolicy.Origin.CLASSIC_CONTEXT).isSafe()).isTrue();
    }

    @Test
    void requireSafeExceptionContainsOnlySafePathAndCategory() {
        assertThatExceptionOfType(HyperionSecretMaterialPolicy.SecretMaterialException.class)
                .isThrownBy(() -> policy.requireSafe("solution/src/fixture.txt", bytes("before " + GITHUB_SENTINEL + " after"),
                        HyperionSecretMaterialPolicy.Origin.GENERATED_CANDIDATE))
                .withMessageContaining("solution/src/fixture.txt").withMessageContaining("GITHUB_TOKEN").withMessageNotContaining(GITHUB_SENTINEL)
                .withMessageNotContaining("before").withMessageNotContaining("after");
    }

    @ParameterizedTest
    @ValueSource(strings = { "AKIAIOSFODNN7EXAMPLE", GITHUB_SENTINEL, "glpat-abcdefghijklmnopqrst" })
    void diagnosticPathIsRedactedWhenItContainsMatchingMaterial(String sentinel) {
        HyperionSecretMaterialPolicy.Assessment assessment = policy.assess("solution/" + sentinel + ".txt", bytes("ordinary"),
                HyperionSecretMaterialPolicy.Origin.GENERATED_CANDIDATE);

        assertThat(assessment.safePath()).isEqualTo("<redacted-path>").doesNotContain(sentinel);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
