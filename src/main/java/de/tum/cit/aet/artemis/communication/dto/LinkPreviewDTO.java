package de.tum.cit.aet.artemis.communication.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information of a link.
 *
 * <p>
 * Serializable because {@code LinkPreviewService} caches it under {@code linkPreview}, a cache the routing cache manager
 * serves from the distributed data provider. Values of a distributed map are encoded with Java serialization, so a link
 * preview that does not implement this answers 500 on the first request that misses the cache.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LinkPreviewDTO(String title, String description, String image, String url) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
