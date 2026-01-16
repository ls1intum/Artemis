package de.tum.cit.aet.artemis.core.config.weaviate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Weaviate vector database integration.
 * These properties control how Artemis connects to and interacts with Weaviate.
 */
@ConfigurationProperties(prefix = "artemis.weaviate")
public class WeaviateConfigurationProperties {

    /**
     * Whether Weaviate integration is enabled.
     * When true, Weaviate beans will be loaded and connections established.
     */
    private boolean enabled = false;

    /**
     * The hostname or IP address of the Weaviate server.
     */
    private String host = "localhost";

    /**
     * The REST API port of the Weaviate server.
     */
    private int port = 8001;

    /**
     * The gRPC port of the Weaviate server.
     */
    private int grpcPort = 50051;

    /**
     * Whether to use a secure (HTTPS/TLS) connection.
     */
    private boolean secure = false;

    /**
     * Schema validation configuration.
     */
    private SchemaValidation schemaValidation = new SchemaValidation();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    public void setGrpcPort(int grpcPort) {
        this.grpcPort = grpcPort;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public SchemaValidation getSchemaValidation() {
        return schemaValidation;
    }

    public void setSchemaValidation(SchemaValidation schemaValidation) {
        this.schemaValidation = schemaValidation;
    }

    /**
     * Configuration for Weaviate schema validation against Iris repository schemas.
     */
    public static class SchemaValidation {

        /**
         * Whether schema validation is enabled. When enabled, Artemis will validate
         * its schema definitions against the Iris repository schemas on startup.
         */
        private boolean enabled = true;

        /**
         * Whether schema validation failures should cause the server to fail to start.
         * When false, validation failures are logged as warnings instead of errors.
         */
        private boolean strict = true;

        /**
         * The URL to fetch schema definitions from the Iris repository.
         * This should point to the raw content of the schema files in the Iris GitHub repository.
         */
        private String irisSchemaBaseUrl = "https://raw.githubusercontent.com/ls1intum/edutelligence/main/iris/src/iris/vector_database";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isStrict() {
            return strict;
        }

        public void setStrict(boolean strict) {
            this.strict = strict;
        }

        public String getIrisSchemaBaseUrl() {
            return irisSchemaBaseUrl;
        }

        public void setIrisSchemaBaseUrl(String irisSchemaBaseUrl) {
            this.irisSchemaBaseUrl = irisSchemaBaseUrl;
        }
    }
}
