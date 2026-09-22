package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Filter;
import io.weaviate.client6.v1.api.collections.query.FilterOperand;

/**
 * Reads the {@code SearchableEntities} collection itself, a bounded slice at a time and resumably.
 * <p>
 * Every other consistency check compares the database against the sync ledger, and both of those live in
 * Postgres. This is the only way to look at what the index actually holds.
 * <p>
 * The walk uses cursor pagination rather than offsets, because Weaviate caps {@code offset + limit} at
 * {@code QUERY_MAXIMUM_RESULTS} (10,000 by default) and everything past that is simply unreachable by offset. A
 * cursor has no such ceiling and doubles as the resume token, which a pass that stops after a bounded slice needs
 * anyway.
 * <p>
 * A cursor walks in UUID order, not insertion order, so a row added behind the current position is picked up on
 * the next cycle rather than this one. That is fine for a pass that runs forever.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityIndexScanService {

    /**
     * Only the properties needed to decide whether a row is correct. The walk covers the whole collection, so
     * pulling titles and bodies along with it would cost a great deal for nothing.
     */
    private static final String[] SCANNED_PROPERTIES = { SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.Properties.ENTITY_ID,
            SearchableEntitySchema.Properties.SOURCE_SEQ, SearchableEntitySchema.Properties.CONTENT_HASH };

    private final WeaviateService weaviateService;

    public SearchableEntityIndexScanService(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
    }

    /**
     * What one indexed row claims about itself, reduced to what a reconcile pass needs.
     *
     * @param uuid        the row's Weaviate id, which doubles as the cursor to resume after it
     * @param entityType  the {@code SearchableEntitySchema.TypeValues} discriminator, null on a malformed row
     * @param entityId    the database id the row claims to represent, null on a malformed row
     * @param sourceSeq   the outbox id of the write that produced the row, null on rows written before that existed
     * @param contentHash the content hash stored with the row, null on rows written before that existed
     */
    public record IndexedRow(String uuid, String entityType, Long entityId, Long sourceSeq, String contentHash) {
    }

    /**
     * One bounded slice of the collection.
     *
     * @param rows       the rows read, in cursor order
     * @param nextCursor where to resume, or null once the collection has been walked to the end
     */
    public record IndexScanSlice(List<IndexedRow> rows, String nextCursor) {
    }

    /**
     * Reads up to {@code pageSize * maxPages} rows, starting after the given cursor.
     *
     * @param cursor   where to resume, or null to start from the beginning of the collection
     * @param pageSize rows fetched per request
     * @param maxPages how many such requests this slice may make
     * @return the rows read, and where to resume; a null cursor in the result means the walk reached the end
     */
    public IndexScanSlice scanFrom(String cursor, int pageSize, int maxPages) {
        int budget = pageSize * maxPages;
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            var paginator = collection.paginate(builder -> {
                builder.pageSize(pageSize).returnProperties(SCANNED_PROPERTIES);
                if (cursor != null) {
                    builder.fromCursor(cursor);
                }
                return builder;
            });

            // The paginator would happily walk the entire collection; the limit is what keeps a tick bounded, and
            // the stream is lazy, so it only fetches the pages it actually consumes.
            List<IndexedRow> rows = paginator.stream().limit(budget).map(SearchableEntityIndexScanService::toIndexedRow).toList();

            // A short slice means the collection ran out, so the next tick starts a fresh cycle rather than
            // resuming at the end of the old one.
            String nextCursor = rows.size() < budget ? null : rows.getLast().uuid();
            return new IndexScanSlice(rows, nextCursor);
        }
        catch (Exception e) {
            throw new WeaviateException("Failed to scan the " + SearchableEntitySchema.COLLECTION_NAME + " collection: " + e.getMessage(), e);
        }
    }

    /**
     * Which of the given ids of one type currently have a row in the index.
     * <p>
     * A confirmed sync-ledger entry proves only that a write once succeeded, not that the row still exists: an
     * external loss, such as restoring Weaviate from a snapshot older than the ledger, leaves the ledger row
     * behind with nothing backing it. The missing sweep uses this to catch exactly that case for the entities its
     * ledger check would otherwise treat as settled.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator, shared by every given id
     * @param entityIds  the database ids to check
     * @return the subset that has a matching row in the index
     */
    public Set<Long> existingEntityIds(String entityType, List<Long> entityIds) {
        if (entityIds.isEmpty()) {
            return Set.of();
        }
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            List<FilterOperand> idOperands = entityIds.stream().<FilterOperand>map(id -> Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(id)).toList();
            Filter filter = Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(entityType), Filter.or(idOperands));
            var result = collection.query.fetchObjects(builder -> builder.limit(entityIds.size()).returnProperties(SearchableEntitySchema.Properties.ENTITY_ID).filters(filter));
            return result.objects().stream().map(object -> asLong(object.properties().get(SearchableEntitySchema.Properties.ENTITY_ID))).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        catch (Exception e) {
            throw new WeaviateException("Failed to check index presence in the " + SearchableEntitySchema.COLLECTION_NAME + " collection: " + e.getMessage(), e);
        }
    }

    private static IndexedRow toIndexedRow(WeaviateObject<Map<String, Object>> object) {
        Map<String, Object> properties = object.properties();
        return new IndexedRow(object.uuid(), asString(properties.get(SearchableEntitySchema.Properties.TYPE)), asLong(properties.get(SearchableEntitySchema.Properties.ENTITY_ID)),
                asLong(properties.get(SearchableEntitySchema.Properties.SOURCE_SEQ)), asString(properties.get(SearchableEntitySchema.Properties.CONTENT_HASH)));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
