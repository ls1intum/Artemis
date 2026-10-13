package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryLectureUnitProperties;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.seedRow;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOperation;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureUnitSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;

/**
 * Integration test for the bulk-delete fence on an upgraded installation: rows indexed while the collection had no
 * {@code source_seq} property, which Weaviate adds later without indexing a null state for the objects already there.
 * <p>
 * It works on its own collection under a dedicated prefix and applies the outbox entry directly, so neither the shared
 * collection nor the running dispatcher is involved.
 */
@EnabledIf("isWeaviateEnabled")
class SearchableEntityBulkDeleteFenceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String COLLECTION_PREFIX = "FenceTest_";

    @Autowired
    private WeaviateClient weaviateClient;

    @Autowired
    private WeaviateConfigurationProperties weaviateConfigurationProperties;

    @Autowired
    private Environment environment;

    @Autowired
    private WeaviateOutboxRepository outboxRepository;

    @Autowired
    private SearchableEntityResolver resolver;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SearchableEntityContentHasher contentHasher;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @AfterEach
    void dropCollection() throws Exception {
        weaviateClient.collections.delete(COLLECTION_PREFIX + SearchableEntitySchema.COLLECTION_NAME);
    }

    @Test
    void testBulkDelete_removesRowsIndexedBeforeSourceSeqWasAddedToTheCollection() throws Exception {
        // The collection as develop created it: every current property except source_seq.
        List<Property> propertiesBeforeTheUpgrade = weaviateClient.collections
                .getConfig(weaviateConfigurationProperties.collectionPrefix() + SearchableEntitySchema.COLLECTION_NAME).orElseThrow().properties().stream()
                .filter(property -> !SearchableEntitySchema.Properties.SOURCE_SEQ.equals(property.propertyName())).toList();
        weaviateClient.collections.create(COLLECTION_PREFIX + SearchableEntitySchema.COLLECTION_NAME, config -> {
            config.vectorConfig(VectorConfig.selfProvided());
            config.invertedIndex(index -> index.indexNulls(true));
            return config.properties(propertiesBeforeTheUpgrade);
        });
        var properties = weaviateConfigurationProperties;
        var weaviateService = new WeaviateService(Optional.of(weaviateClient),
                new WeaviateConfigurationProperties(properties.enabled(), properties.httpHost(), properties.httpPort(), properties.grpcPort(), properties.scheme(),
                        COLLECTION_PREFIX, properties.vectorizerModule(), properties.openAiEmbeddingModel(), properties.openAiBaseUrl(), properties.gpuApiKey(),
                        properties.apiKey()),
                environment);
        var searchableEntityWeaviateService = new SearchableEntityWeaviateService(weaviateService, outboxRepository, resolver, jsonMapper, contentHasher, eventPublisher);

        long courseId = 991000, lectureId = 991001, otherLectureId = 991009, legacyUnitId = 991002, otherLegacyUnitId = 991008, newerUnitId = 991003;
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.LECTURE_UNIT, legacyUnitId,
                new LectureUnitSearchableEntityDTO(legacyUnitId, courseId, lectureId, "unit", "desc", "text", null).toPropertyMap());
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.LECTURE_UNIT, otherLegacyUnitId,
                new LectureUnitSearchableEntityDTO(otherLegacyUnitId, courseId, otherLectureId, "unit", "desc", "text", null).toPropertyMap());
        // The startup ensure of the upgraded version adds source_seq to the existing collection.
        weaviateService.ensureAllCollectionsExist();
        // Written by an upsert enqueued after the delete, so its source_seq is larger than the delete's outbox id.
        Map<String, Object> newerProperties = new HashMap<>(new LectureUnitSearchableEntityDTO(newerUnitId, courseId, lectureId, "newer", "desc", "text", null).toPropertyMap());
        newerProperties.put(SearchableEntitySchema.Properties.SOURCE_SEQ, 200L);
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.LECTURE_UNIT, newerUnitId, newerProperties);

        WeaviateOutboxEntry delete = WeaviateOutboxEntry.forBulkDelete(WeaviateOutboxOperation.DELETE_LECTURE_UNITS_FOR_LECTURE, "{\"lectureId\":" + lectureId + "}",
                WeaviateOutboxOrigin.LIVE);
        delete.setId(100L);
        searchableEntityWeaviateService.applyOutboxEntry(delete);

        assertThat(queryLectureUnitProperties(weaviateService, legacyUnitId)).as("legacy unit of the deleted lecture").isNull();
        assertThat(queryLectureUnitProperties(weaviateService, newerUnitId)).as("unit written after the delete").isNotNull();
        assertThat(queryLectureUnitProperties(weaviateService, otherLegacyUnitId)).as("legacy unit of another lecture").isNotNull();
    }
}
