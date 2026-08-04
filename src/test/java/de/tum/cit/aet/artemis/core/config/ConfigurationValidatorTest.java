package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.core.exception.InsecureDefaultCredentialException;
import de.tum.cit.aet.artemis.globalsearch.config.SupportedVectorizer;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateConfigurationException;

/**
 * Tests for ConfigurationValidator
 */
class ConfigurationValidatorTest {

    private static final String VALID_HOST = "localhost";

    private static final int VALID_HTTP_PORT = 8001;

    private static final int VALID_GRPC_PORT = 50051;

    private static final String VALID_SCHEME = "http";

    private static final String VALID_VECTORIZER_MODULE = WeaviateConfigurationProperties.DEFAULT_VECTORIZER_MODULE;

    private ConfigurationValidator createValidator(boolean weaviateEnabled, String weaviateHost, int weaviatePort, int weaviateGrpcPort, String weaviateScheme) {
        return createValidator(weaviateEnabled, weaviateHost, weaviatePort, weaviateGrpcPort, weaviateScheme, VALID_VECTORIZER_MODULE, null, null);
    }

    private ConfigurationValidator createValidator(boolean weaviateEnabled, String weaviateHost, int weaviatePort, int weaviateGrpcPort, String weaviateScheme,
            String vectorizerModule) {
        return createValidator(weaviateEnabled, weaviateHost, weaviatePort, weaviateGrpcPort, weaviateScheme, vectorizerModule, null, null);
    }

    private ConfigurationValidator createValidator(boolean weaviateEnabled, String weaviateHost, int weaviatePort, int weaviateGrpcPort, String weaviateScheme,
            String vectorizerModule, String openAiBaseUrl, String gpuApiKey) {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getProperty(Constants.PASSKEY_ENABLED_PROPERTY_NAME, Boolean.class)).thenReturn(false);
        return new ConfigurationValidator(mockEnvironment, false, null, null, weaviateEnabled, weaviateHost, weaviatePort, weaviateGrpcPort, weaviateScheme, vectorizerModule,
                openAiBaseUrl, gpuApiKey, false, "http://localhost");
    }

    /**
     * Builds a validator whose {@link Environment} reports the given active profiles and property values,
     * for the production shipped-default credential checks.
     */
    private ConfigurationValidator createCredentialValidator(boolean productionProfileActive, String jwtBase64Secret, String plainJwtSecret, String internalAdminUsername,
            String internalAdminPassword, String buildAgentGitPassword) {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getProperty(Constants.PASSKEY_ENABLED_PROPERTY_NAME, Boolean.class)).thenReturn(false);
        when(mockEnvironment.matchesProfiles(ArtemisConstants.SPRING_PROFILE_PRODUCTION)).thenReturn(productionProfileActive);
        when(mockEnvironment.getProperty("jhipster.security.authentication.jwt.base64-secret")).thenReturn(jwtBase64Secret);
        when(mockEnvironment.getProperty("jhipster.security.authentication.jwt.secret")).thenReturn(plainJwtSecret);
        when(mockEnvironment.getProperty("artemis.version-control.build-agent-git-password")).thenReturn(buildAgentGitPassword);
        return new ConfigurationValidator(mockEnvironment, false, internalAdminUsername, internalAdminPassword, false, null, VALID_HTTP_PORT, VALID_GRPC_PORT, null, null, null,
                null, false, "http://localhost");
    }

    @Nested
    class ShippedDefaultCredentialTest {

        /** A 64-byte key, the minimum HS512 accepts, that is not one of the shipped examples. */
        private static final String ACCEPTABLE_BASE64_SECRET = java.util.Base64.getEncoder().encodeToString("k".repeat(64).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        private static final String SHIPPED_BASE64_SECRET = "bXktc2VjcmV0LWtleS13aGljaC1zaG91bGQtYmUtY2hhbmdlZC1pbi1wcm9kdWN0aW9uLWFuZC1iZS1iYXNlNjQtZW5jb2RlZAo=";

        /** The key the docker prod fixtures carried in a pre-merge revision of this change. */
        private static final String FORMER_FIXTURE_SECRET = "R6Mmos1yXM2Psu1SJ3wkTEM0g1r4w3EPcCS8CY6BvGllthhMfch6kp7/d3qJS4Nh+XE5ng9Eb6sE34ybi56f9A==";

        @Test
        void testShippedJwtSecretIsRejectedUnderProdProfile() {
            ConfigurationValidator validator = createCredentialValidator(true, SHIPPED_BASE64_SECRET, null, null, null, null);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class)
                    .hasMessageContaining("jhipster.security.authentication.jwt.base64-secret");
        }

        @Test
        void testShippedJwtSecretIsToleratedWithoutProdProfile() {
            // dev and test deployments must keep working with the packaged example values
            ConfigurationValidator validator = createCredentialValidator(false, SHIPPED_BASE64_SECRET, null, "artemis_admin", "artemis_admin", "buildjob_password");

            assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
        }

        @Test
        void testShippedJwtSecretIsRejectedViaThePlainSecretProperty() {
            // TokenProvider prefers the plain secret over base64-secret, so the decoded default must be caught there too
            ConfigurationValidator validator = createCredentialValidator(true, ACCEPTABLE_BASE64_SECRET,
                    "my-secret-key-which-should-be-changed-in-production-and-be-base64-encoded", null, null, null);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class)
                    .hasMessageContaining("jhipster.security.authentication.jwt.secret");
        }

        /**
         * A shipped key has more than one Base64 spelling, and only one of them is listed as a literal. Encoding the
         * published plaintext without its trailing newline yields a different string that still decodes to the very same
         * publicly known signing key, so the check has to look at the decoded bytes.
         */
        @Test
        void testAReEncodedShippedJwtSecretIsRejectedUnderProdProfile() {
            String reEncodedShippedSecret = java.util.Base64.getEncoder()
                    .encodeToString("my-secret-key-which-should-be-changed-in-production-and-be-base64-encoded".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ConfigurationValidator validator = createCredentialValidator(true, reEncodedShippedSecret, null, null, null, null);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class)
                    .hasMessageContaining("published in the Artemis repository");
        }

        /**
         * The docker prod fixtures briefly carried this key before they switched to generating one per run, so it is
         * readable in this repository's history and has to keep failing for anyone who copied it in the meantime.
         */
        @Test
        void testAKeyPublishedByTheDockerFixturesIsRejectedUnderProdProfile() {
            ConfigurationValidator validator = createCredentialValidator(true, FORMER_FIXTURE_SECRET, null, null, null, null);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class)
                    .hasMessageContaining("published in the Artemis repository");
        }

        /**
         * Dropping the padding gives a spelling that is not listed as a literal but decodes to the identical key, which is
         * why the listed Base64 entries are compared decoded as well.
         */
        @Test
        void testAReSpelledPublishedKeyIsRejectedUnderProdProfile() {
            String unpaddedSecret = FORMER_FIXTURE_SECRET.replace("=", "");

            ConfigurationValidator validator = createCredentialValidator(true, unpaddedSecret, null, null, null, null);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class)
                    .hasMessageContaining("published in the Artemis repository");
        }

        @Test
        void testMissingJwtSecretIsRejectedUnderProdProfile() {
            ConfigurationValidator validator = createCredentialValidator(true, null, null, null, null, null);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class).hasMessageContaining("no JWT signing key is configured");
        }

        @Test
        void testTooShortJwtSecretIsRejectedUnderProdProfile() {
            String shortSecret = java.util.Base64.getEncoder().encodeToString("tooshort".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ConfigurationValidator validator = createCredentialValidator(true, shortSecret, null, null, null, null);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class).hasMessageContaining("HS512 requires at least");
        }

        /**
         * Every internal-admin password the repository publishes has to be rejected, not just the one in
         * {@code config/application-artemis.yml}: an operator following the production-setup security documentation
         * copies {@code artemis-admin}, which is just as public.
         *
         * @param publishedPassword an internal-admin password published in the repository
         */
        @ParameterizedTest
        @ValueSource(strings = { "artemis_admin", "artemis-admin", "SecureP@ss123" })
        void testShippedInternalAdminPasswordIsRejectedUnderProdProfile(String publishedPassword) {
            ConfigurationValidator validator = createCredentialValidator(true, ACCEPTABLE_BASE64_SECRET, null, "some_admin", publishedPassword, null);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class)
                    .hasMessageContaining("artemis.user-management.internal-admin.password");
        }

        @Test
        void testInternalAdminPasswordEqualToUsernameIsRejectedUnderProdProfile() {
            ConfigurationValidator validator = createCredentialValidator(true, ACCEPTABLE_BASE64_SECRET, null, "reused_value", "reused_value", null);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class)
                    .hasMessageContaining("identical to the internal admin username");
        }

        /**
         * A matching build-agent git password grants read access to every repository, so each value the repository
         * publishes for it has to be rejected: the packaged {@code buildjob_password}, the {@code buildagent_password}
         * of the production-setup security documentation, and the {@code artemis_admin} that the Jenkins LocalVC setup
         * reuses here.
         *
         * @param publishedPassword a build-agent git password published in the repository
         */
        @ParameterizedTest
        @ValueSource(strings = { "buildjob_password", "buildagent_password", "artemis_admin" })
        void testShippedBuildAgentGitPasswordIsRejectedUnderProdProfile(String publishedPassword) {
            ConfigurationValidator validator = createCredentialValidator(true, ACCEPTABLE_BASE64_SECRET, null, null, null, publishedPassword);

            assertThatThrownBy(validator::validateConfigurations).isInstanceOf(InsecureDefaultCredentialException.class)
                    .hasMessageContaining("artemis.version-control.build-agent-git-password");
        }

        @Test
        void testAcceptableProductionCredentialsPassValidation() {
            ConfigurationValidator validator = createCredentialValidator(true, ACCEPTABLE_BASE64_SECRET, null, "operator_admin", "a-unique-strong-password",
                    "a-unique-build-agent-password");

            assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
        }

        @Test
        void testNoInternalAdminConfiguredIsAcceptableUnderProdProfile() {
            // the internal admin account is optional; omitting it entirely must remain valid
            ConfigurationValidator validator = createCredentialValidator(true, ACCEPTABLE_BASE64_SECRET, null, null, null, null);

            assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
        }
    }

    @Nested
    class WeaviateConfigurationTest {

        @Test
        void testWeaviateDisabledShouldSkipValidation() {
            ConfigurationValidator validator = createValidator(false, null, 0, 0, null);

            assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
        }

        @Nested
        class SchemeValidationTest {

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = { "   ", "\t", "\n" })
            void testNullOrBlankShouldFailValidation(String scheme) {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, scheme);

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class)
                        .hasMessageContaining("artemis.weaviate.scheme (must be configured when Weaviate is enabled)");
            }

            @ParameterizedTest
            @ValueSource(strings = { "ftp", "wss", "tcp", "HTTP", "HTTPS", "Http" })
            void testInvalidValueShouldFailValidation(String scheme) {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, scheme);

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class)
                        .hasMessageContaining("artemis.weaviate.scheme (must be 'http' or 'https')");
            }

            @Test
            void testHttpShouldPassValidation() {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, "http");

                assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
            }

            @Test
            void testHttpsShouldPassValidation() {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, "https");

                assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
            }
        }

        @Nested
        class HostValidationTest {

            @Test
            void testNullShouldFailValidation() {
                ConfigurationValidator validator = createValidator(true, null, VALID_HTTP_PORT, VALID_GRPC_PORT, VALID_SCHEME);

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class)
                        .hasMessageContaining("artemis.weaviate.http-host (must not be empty)");
            }

            @ParameterizedTest
            @ValueSource(strings = { "", "   ", "\t", "\n" })
            void testBlankShouldFailValidation(String host) {
                ConfigurationValidator validator = createValidator(true, host, VALID_HTTP_PORT, VALID_GRPC_PORT, VALID_SCHEME);

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class)
                        .hasMessageContaining("artemis.weaviate.http-host (must not be empty)");
            }
        }

        @Nested
        class HttpPortValidationTest {

            @ParameterizedTest
            @ValueSource(ints = { 0, -1, -100, 65536, 70000 })
            void testInvalidPortShouldFailValidation(int port) {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, port, VALID_GRPC_PORT, VALID_SCHEME);

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class)
                        .hasMessageContaining("artemis.weaviate.http-port (must be between 1 and 65535)");
            }

            @ParameterizedTest
            @ValueSource(ints = { 1, 80, 443, 8080, 65535 })
            void testValidPortShouldPassValidation(int port) {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, port, VALID_GRPC_PORT, VALID_SCHEME);

                assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
            }
        }

        @Nested
        class GrpcPortValidationTest {

            @ParameterizedTest
            @ValueSource(ints = { 0, -1, -100, 65536, 70000 })
            void testInvalidPortShouldFailValidation(int port) {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, port, VALID_SCHEME);

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class)
                        .hasMessageContaining("artemis.weaviate.grpc-port (must be between 1 and 65535)");
            }

            @ParameterizedTest
            @ValueSource(ints = { 1, 50051, 50052, 65535 })
            void testValidPortShouldPassValidation(int port) {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, port, VALID_SCHEME);

                assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
            }
        }

        @Nested
        class VectorizerModuleValidationTest {

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = { "   ", "\t", "\n" })
            void testNullOrBlankShouldFailValidation(String vectorizerModule) {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, VALID_SCHEME, vectorizerModule);

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class)
                        .hasMessageContaining("artemis.weaviate.vectorizer-module (must be configured when Weaviate is enabled)");
            }

            @ParameterizedTest
            @ValueSource(strings = { "text2vec-transformer", "invalid", "None", "TEXT2VEC-TRANSFORMERS" })
            void testInvalidValueShouldFailValidation(String vectorizerModule) {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, VALID_SCHEME, vectorizerModule);

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class).hasMessageContaining("artemis.weaviate.vectorizer-module");
            }

            @Test
            void testNoneShouldPassValidation() {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, VALID_SCHEME,
                        WeaviateConfigurationProperties.DEFAULT_VECTORIZER_MODULE);

                assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
            }

            @Test
            void testText2vecTransformersShouldPassValidation() {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, VALID_SCHEME,
                        SupportedVectorizer.TEXT2VEC_TRANSFORMERS.configValue());

                assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
            }

            @Test
            void testText2vecOpenAiWithApiPropertiesShouldPassValidation() {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, VALID_SCHEME,
                        SupportedVectorizer.TEXT2VEC_OPENAI.configValue(), "http://localhost:11434", "dummy");

                assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
            }

            @Test
            void testText2vecOpenAiWithoutBaseUrlShouldFailValidation() {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, VALID_SCHEME,
                        SupportedVectorizer.TEXT2VEC_OPENAI.configValue(), null, "dummy");

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class).hasMessageContaining("artemis.weaviate.open-ai-base-url");
            }

            @Test
            void testText2vecOpenAiWithoutApiKeyShouldFailValidation() {
                ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, VALID_SCHEME,
                        SupportedVectorizer.TEXT2VEC_OPENAI.configValue(), "http://localhost:11434", null);

                assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class).hasMessageContaining("artemis.weaviate.gpu-api-key");
            }
        }
    }
}
