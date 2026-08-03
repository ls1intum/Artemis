package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Counts how many rows of a given type are indexed for a course in the shared {@code SearchableEntities}
 * collection, using a filtered Weaviate aggregate. Read-only.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityCountService {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityCountService.class);

    private final WeaviateService weaviateService;

    public SearchableEntityCountService(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
    }

    /**
     * The number of {@code SearchableEntities} rows of the given type currently indexed for the course.
     * Returns 0 when the collection cannot be read (for example it does not exist yet).
     *
     * @param courseId the course id
     * @param type     the entity type discriminator
     * @return the indexed row count, or 0 when unavailable
     */
    public long countIndexed(long courseId, String type) {
        try {
            CollectionHandle<Map<String, Object>> collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            Filter filter = Filter.and(Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(courseId), Filter.property(SearchableEntitySchema.Properties.TYPE).eq(type));
            Long total = collection.aggregate.overAll(builder -> builder.filters(filter).includeTotalCount(true)).totalCount();
            return total != null ? total : 0L;
        }
        catch (Exception e) {
            log.debug("Could not count SearchableEntities type '{}' for course {}: {}", type, courseId, e.getMessage());
            return 0L;
        }
    }
}
