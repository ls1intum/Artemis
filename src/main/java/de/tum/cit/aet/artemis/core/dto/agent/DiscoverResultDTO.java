package de.tum.cit.aet.artemis.core.dto.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The result of {@code server/discover}, with which a client learns what the server speaks and offers before it asks for anything else.
 *
 * @param resultType        always {@code complete}
 * @param supportedVersions the protocol versions the server answers to
 * @param capabilities      what the server offers
 * @param instructions      what the model should know before it uses the tools
 * @param ttlMs             how long a client may reuse this result, in milliseconds
 * @param cacheScope        {@code public} if any cache may serve the result to anyone, {@code private} if only to the same token
 * @param meta              which server answers
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DiscoverResultDTO(String resultType, List<String> supportedVersions, CapabilitiesDTO capabilities, String instructions, long ttlMs, String cacheScope,
        @JsonProperty("_meta") ResultMetaDTO meta) {
}
