package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateHealthIndicator;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexCollectionCountDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexOverviewDTO;

/**
 * Builds the read-only overview for the admin Course Content Ingestion Dashboard: Weaviate
 * reachability plus the object count of each collection that global search and the Iris tutor use.
 * <p>
 * This reads live from Weaviate on demand; there is no persisted state yet. Per-course completeness
 * is added later, once the reconcile sync-state exists.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class GlobalSearchIndexAdminService {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchIndexAdminService.class);

    /**
     * The Pyris/Iris-owned lecture-content collections. These are created by Iris WITHOUT the Artemis collection
     * prefix (the prefix exists precisely to avoid colliding with them), so they must be read by their exact name via
     * {@link WeaviateService#getExternalCollection(String)} rather than the prefix-applying
     * {@link WeaviateService#getCollection(String)}.
     */
    private static final List<String> PYRIS_COLLECTIONS = List.of("Lectures", "LectureTranscriptions", "LectureUnits", "LectureUnitSegments");

    private final WeaviateService weaviateService;

    private final Optional<WeaviateHealthIndicator> weaviateHealthIndicator;

    public GlobalSearchIndexAdminService(WeaviateService weaviateService, Optional<WeaviateHealthIndicator> weaviateHealthIndicator) {
        this.weaviateService = weaviateService;
        this.weaviateHealthIndicator = weaviateHealthIndicator;
    }

    /**
     * Reads Weaviate health and the object count of every tracked collection.
     * <p>
     * A collection that cannot be read (for example because nothing of that kind has been ingested yet, so
     * the collection does not exist) is reported as unavailable rather than failing the whole request.
     *
     * @return the index overview snapshot
     */
    public IndexOverviewDTO getOverview() {
        Health health = weaviateHealthIndicator.map(WeaviateHealthIndicator::health).orElse(null);
        boolean up = health != null && Status.UP.equals(health.getStatus());
        String address = health != null && health.getDetails().get("Address") != null ? String.valueOf(health.getDetails().get("Address")) : null;

        List<IndexCollectionCountDTO> counts = new ArrayList<>();
        // SearchableEntities is Artemis-owned, so it is read through the prefix-applying accessor.
        counts.add(countArtemisCollection(SearchableEntitySchema.COLLECTION_NAME));
        // The Pyris lecture-content collections are unprefixed, so they are read by their exact name.
        for (String collection : PYRIS_COLLECTIONS) {
            counts.add(countExternalCollection(collection));
        }
        return new IndexOverviewDTO(up, address, counts);
    }

    private IndexCollectionCountDTO countArtemisCollection(String collection) {
        try {
            return IndexCollectionCountDTO.available(collection, weaviateService.getCollection(collection).size());
        }
        catch (Exception e) {
            log.debug("Could not read Artemis Weaviate collection '{}' for the index overview: {}", collection, e.getMessage());
            return IndexCollectionCountDTO.unavailable(collection);
        }
    }

    private IndexCollectionCountDTO countExternalCollection(String collection) {
        try {
            return IndexCollectionCountDTO.available(collection, weaviateService.getExternalCollection(collection).size());
        }
        catch (Exception e) {
            log.debug("Could not read external Weaviate collection '{}' for the index overview: {}", collection, e.getMessage());
            return IndexCollectionCountDTO.unavailable(collection);
        }
    }
}
