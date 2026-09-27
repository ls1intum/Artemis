package de.tum.cit.aet.artemis.globalsearch.util;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;

/**
 * Seeds the index and the database for the ingestion-coverage tests: {@code SearchableEntities} rows, objects in the four
 * Iris content collections (created under their exact, unprefixed names, as the Pyris pipeline names them), and lecture
 * units with a video source or an attachment.
 */
public final class IngestionCoverageTestUtil {

    public static final String CONTENT_COURSE_ID = "course_id";

    public static final String CONTENT_LECTURE_UNIT_ID = "lecture_unit_id";

    public static final String CONTENT_PAGE_NUMBER = "page_number";

    public static final List<String> IRIS_CONTENT_COLLECTIONS = List.of(IngestionCoverageWeaviateReadService.LECTURES_COLLECTION,
            IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION, IngestionCoverageWeaviateReadService.LECTURE_UNIT_SEGMENTS_COLLECTION,
            IngestionCoverageWeaviateReadService.LECTURE_UNITS_COLLECTION);

    private IngestionCoverageTestUtil() {
    }

    /** Inserts a {@code SearchableEntities} row; a {@code null} title leaves the property unset. */
    public static void insertMetadata(WeaviateService weaviateService, long courseId, String type, long entityId, @Nullable String title) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TYPE, type);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, entityId);
        if (title != null) {
            properties.put(SearchableEntitySchema.Properties.TITLE, title);
        }
        weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).data.insert(properties);
    }

    /** Inserts an object into an Iris content collection; a {@code null} page number leaves the property unset. */
    public static void insertContent(WeaviateService weaviateService, String collectionName, long courseId, long unitId, @Nullable Integer pageNumber) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CONTENT_COURSE_ID, courseId);
        properties.put(CONTENT_LECTURE_UNIT_ID, unitId);
        if (pageNumber != null) {
            properties.put(CONTENT_PAGE_NUMBER, pageNumber);
        }
        weaviateService.getExternalCollection(collectionName).data.insert(properties);
    }

    /** Drops and recreates the four Iris content collections, empty. */
    public static void recreateIrisContentCollections(WeaviateClient weaviateClient) throws Exception {
        dropIrisContentCollections(weaviateClient);
        for (String name : IRIS_CONTENT_COLLECTIONS) {
            weaviateClient.collections.create(name, collection -> {
                collection.vectorConfig(VectorConfig.selfProvided());
                collection.properties(Property.integer(CONTENT_COURSE_ID));
                collection.properties(Property.integer(CONTENT_LECTURE_UNIT_ID));
                collection.properties(Property.integer(CONTENT_PAGE_NUMBER));
                return collection;
            });
        }
    }

    /** Drops whichever of the four Iris content collections exist. */
    public static void dropIrisContentCollections(WeaviateClient weaviateClient) throws Exception {
        for (String name : IRIS_CONTENT_COLLECTIONS) {
            if (weaviateClient.collections.exists(name)) {
                weaviateClient.collections.delete(name);
            }
        }
    }

    /** Saves an attachment/video unit with the given video source. */
    public static AttachmentVideoUnit seedUnitWithVideoSource(AttachmentVideoUnitRepository unitRepository, Lecture lecture, @Nullable String name, String videoSource) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setName(name);
        unit.setDescription("Test");
        unit.setLecture(lecture);
        unit.setVideoSource(videoSource);
        return unitRepository.save(unit);
    }

    /** Saves an attachment/video unit whose attachment has the given link and type. */
    public static AttachmentVideoUnit seedUnitWithAttachment(AttachmentVideoUnitRepository unitRepository, AttachmentRepository attachmentRepository, Lecture lecture,
            @Nullable String name, String link, AttachmentType attachmentType) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setName(name);
        unit.setDescription("Test");
        unit.setLecture(lecture);
        unit = unitRepository.save(unit);

        Attachment attachment = new Attachment();
        attachment.setAttachmentType(attachmentType);
        attachment.setName("Attachment");
        attachment.setVersion(1);
        attachment.setReleaseDate(ZonedDateTime.now().minusDays(1));
        attachment.setUploadDate(ZonedDateTime.now().minusDays(1));
        attachment.setLink(link);
        attachment.setAttachmentVideoUnit(unit);
        attachment = attachmentRepository.save(attachment);

        unit.setAttachment(attachment);
        return unitRepository.save(unit);
    }
}
