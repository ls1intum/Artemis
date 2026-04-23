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

import de.tum.cit.aet.artemis.core.exception.WeaviateConfigurationException;
import de.tum.cit.aet.artemis.globalsearch.config.SupportedVectorizer;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;

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
                openAiBaseUrl, gpuApiKey, false);
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
