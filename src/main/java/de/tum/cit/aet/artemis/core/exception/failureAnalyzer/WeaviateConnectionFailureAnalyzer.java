package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.exception.WeaviateConnectionException;

/**
 * FailureAnalyzer that provides helpful error messages when the connection to Weaviate fails.
 * This analyzer catches {@link WeaviateConnectionException} and formats it into a user-friendly message
 * with guidance on verifying that Weaviate is running and reachable from this server.
 */
public class WeaviateConnectionFailureAnalyzer extends AbstractFailureAnalyzer<WeaviateConnectionException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, WeaviateConnectionException cause) {
        String description = buildDescription(cause);
        String action = buildAction(cause);
        return new FailureAnalysis(description, action, cause);
    }

    private String buildDescription(WeaviateConnectionException cause) {
        String scheme = cause.isSecure() ? "https" : "http";
        return String.format(
                "Failed to connect to Weaviate vector database.%n%n" + "Connection details:%n" + "    Host: %s%n" + "    HTTP Port: %d (%s://%s:%d)%n" + "    gRPC Port: %d%n%n"
                        + "Error: %s%n",
                cause.getHost(), cause.getPort(), scheme, cause.getHost(), cause.getPort(), cause.getGrpcPort(),
                cause.getCause() != null ? cause.getCause().getMessage() : cause.getMessage());
    }

    private String buildAction(WeaviateConnectionException cause) {
        String scheme = cause.isSecure() ? "https" : "http";
        return """
                Please verify the following:

                1. VERIFY WEAVIATE IS RUNNING:
                   - Check if the Weaviate container/service is started
                   - Docker: docker ps | grep weaviate
                   - Docker Compose: docker compose ps
                   - Check Weaviate logs: docker logs <container-name>

                2. VERIFY NETWORK CONNECTIVITY:
                   - Test HTTP endpoint: curl -v %s://%s:%d/v1/.well-known/ready
                   - Test if port is open: nc -zv %s %d
                   - Test gRPC port: nc -zv %s %d

                3. VERIFY CONFIGURATION:
                   - Ensure the host '%s' is resolvable from this server
                   - If running in Docker, ensure proper network configuration
                   - If using Docker Compose, ensure services are on the same network
                   - Check firewall rules for ports %d (HTTP) and %d (gRPC)

                4. COMMON ISSUES:
                   - Weaviate might still be starting up (wait a few seconds)
                   - Host 'localhost' from a container won't reach host machine (use host.docker.internal or actual IP)
                   - gRPC port (default 50051) must also be accessible, not just HTTP port

                5. DISABLE WEAVIATE (if not needed):
                   artemis:
                     weaviate:
                       enabled: false
                """.formatted(scheme, cause.getHost(), cause.getPort(), cause.getHost(), cause.getPort(), cause.getHost(), cause.getGrpcPort(), cause.getHost(), cause.getPort(),
                cause.getGrpcPort());
    }
}
