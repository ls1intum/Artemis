package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private ConfigurationValidator createValidator(boolean weaviateEnabled, String weaviateHost, int weaviatePort, int weaviateGrpcPort, String weaviateScheme) {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getProperty(Constants.PASSKEY_ENABLED_PROPERTY_NAME, Boolean.class)).thenReturn(false);
        return new ConfigurationValidator(mockEnvironment, false, null, null, weaviateEnabled, weaviateHost, weaviatePort, weaviateGrpcPort, weaviateScheme);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    void testWeaviateSchemeNullOrBlankShouldFailValidation(String scheme) {
        ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, scheme);

        assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class)
                .hasMessageContaining("artemis.weaviate.scheme (must be configured when Weaviate is enabled)");
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "wss", "tcp", "HTTP", "HTTPS", "Http" })
    void testWeaviateSchemeInvalidValueShouldFailValidation(String scheme) {
        ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, scheme);

        assertThatThrownBy(validator::validateConfigurations).isInstanceOf(WeaviateConfigurationException.class)
                .hasMessageContaining("artemis.weaviate.scheme (must be 'http' or 'https')");
    }

    @Test
    void testWeaviateSchemeHttpShouldPassValidation() {
        ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, "http");

        assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
    }

    @Test
    void testWeaviateSchemeHttpsShouldPassValidation() {
        ConfigurationValidator validator = createValidator(true, VALID_HOST, VALID_HTTP_PORT, VALID_GRPC_PORT, "https");

        assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
    }

    @Test
    void testWeaviateDisabledShouldSkipSchemeValidation() {
        ConfigurationValidator validator = createValidator(false, null, 0, 0, null);

        assertThatCode(validator::validateConfigurations).doesNotThrowAnyException();
    }
}
