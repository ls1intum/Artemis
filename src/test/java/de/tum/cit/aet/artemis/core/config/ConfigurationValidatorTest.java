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

/**
 * Tests for ConfigurationValidator
 */
class ConfigurationValidatorTest {

    private static final String VALID_HOST = "localhost";

    private static final int VALID_HTTP_PORT = 8001;

    private static final int VALID_GRPC_PORT = 50051;

    private static final String VALID_SCHEME = "http";

    private ConfigurationValidator createValidator(boolean weaviateEnabled, String weaviateHost, int weaviatePort, int weaviateGrpcPort, String weaviateScheme) {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getProperty(Constants.PASSKEY_ENABLED_PROPERTY_NAME, Boolean.class)).thenReturn(false);
        return new ConfigurationValidator(mockEnvironment, false, null, null, weaviateEnabled, weaviateHost, weaviatePort, weaviateGrpcPort, weaviateScheme);
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
    }
}