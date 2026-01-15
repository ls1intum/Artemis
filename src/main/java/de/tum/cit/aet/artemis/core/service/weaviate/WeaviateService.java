package de.tum.cit.aet.artemis.core.service.weaviate;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_WEAVIATE;

import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateCollectionSchema;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateReferenceDefinition;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateSchemas;
import io.weaviate.client6.WeaviateClient;
import io.weaviate.client6.config.collection.Property;
import io.weaviate.client6.config.collection.ReferenceProperty;
import io.weaviate.client6.v1.collections.Collection;

/**
 * Service for interacting with Weaviate vector database.
 * This service handles schema creation, data insertion, and search operations.
 */
@Service
@Profile(PROFILE_WEAVIATE)
public class WeaviateService {

    private static final Logger log = LoggerFactory.getLogger(WeaviateService.class);

    private final WeaviateClient client;

    public WeaviateService(WeaviateClient client) {
        this.client = client;
    }

    /**
     * Initializes the Weaviate collections on startup.
     * Creates collections that don't exist yet.
     */
    @PostConstruct
    public void initializeCollections() {
        log.info("Initializing Weaviate collections...");

        for (WeaviateCollectionSchema schema : WeaviateSchemas.ALL_SCHEMAS) {
            ensureCollectionExists(schema);
        }

        log.info("Weaviate collection initialization complete");
    }

    /**
     * Ensures a collection exists, creating it if necessary.
     *
     * @param schema the schema definition
     */
    private void ensureCollectionExists(WeaviateCollectionSchema schema) {
        String collectionName = schema.collectionName();

        if (client.collections().exists(collectionName)) {
            log.debug("Collection '{}' already exists", collectionName);
            return;
        }

        log.info("Creating collection '{}'...", collectionName);

        try {
            client.collections().create(collectionName, col -> {
                // Add properties
                for (WeaviatePropertyDefinition prop : schema.properties()) {
                    col.properties(createProperty(prop));
                }

                // Add references
                for (WeaviateReferenceDefinition ref : schema.references()) {
                    col.references(ReferenceProperty.to(ref.name(), ref.targetCollection()));
                }

                return col;
            });

            log.info("Successfully created collection '{}'", collectionName);
        }
        catch (Exception e) {
            log.error("Failed to create collection '{}': {}", collectionName, e.getMessage(), e);
            throw new WeaviateException("Failed to create collection: " + collectionName, e);
        }
    }

    /**
     * Creates a Weaviate property from a property definition.
     *
     * @param definition the property definition
     * @return the Weaviate property
     */
    private Property<?> createProperty(WeaviatePropertyDefinition definition) {
        return switch (definition.dataType()) {
            case INT -> Property.integer(definition.name(), p -> p.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case TEXT -> Property.text(definition.name(), p -> p.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case NUMBER -> Property.number(definition.name(), p -> p.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case BOOLEAN -> Property.bool(definition.name(), p -> p.indexFilterable(definition.indexFilterable()));
            case DATE -> Property.date(definition.name(), p -> p.indexFilterable(definition.indexFilterable()));
            case UUID -> Property.uuid(definition.name(), p -> p.indexFilterable(definition.indexFilterable()));
            case BLOB -> Property.blob(definition.name());
        };
    }

    /**
     * Gets a collection handle for the specified collection name.
     *
     * @param collectionName the collection name
     * @return the collection handle
     */
    public Collection<Map<String, Object>> getCollection(String collectionName) {
        return client.collections().use(collectionName);
    }

    /**
     * Inserts an object into the Lectures collection.
     *
     * @param courseId          the course ID
     * @param courseLanguage    the course language
     * @param lectureId         the lecture ID
     * @param lectureUnitId     the lecture unit ID
     * @param pageTextContent   the page text content
     * @param pageNumber        the page number
     * @param baseUrl           the base URL
     * @param attachmentVersion the attachment version
     */
    public void insertLecturePageChunk(long courseId, String courseLanguage, long lectureId, long lectureUnitId, String pageTextContent, int pageNumber, String baseUrl,
            int attachmentVersion) {

        var collection = getCollection(WeaviateSchemas.LECTURES_COLLECTION);

        collection.data()
                .insert(Map.of(WeaviateSchemas.LecturesProperties.COURSE_ID, courseId, WeaviateSchemas.LecturesProperties.COURSE_LANGUAGE, courseLanguage,
                        WeaviateSchemas.LecturesProperties.LECTURE_ID, lectureId, WeaviateSchemas.LecturesProperties.LECTURE_UNIT_ID, lectureUnitId,
                        WeaviateSchemas.LecturesProperties.PAGE_TEXT_CONTENT, pageTextContent, WeaviateSchemas.LecturesProperties.PAGE_NUMBER, pageNumber,
                        WeaviateSchemas.LecturesProperties.BASE_URL, baseUrl, WeaviateSchemas.LecturesProperties.ATTACHMENT_VERSION, attachmentVersion));

        log.debug("Inserted lecture page chunk for lecture unit {} page {}", lectureUnitId, pageNumber);
    }

    /**
     * Inserts an FAQ into the Faqs collection.
     *
     * @param courseId          the course ID
     * @param courseName        the course name
     * @param courseDescription the course description
     * @param courseLanguage    the course language
     * @param faqId             the FAQ ID
     * @param questionTitle     the question title
     * @param questionAnswer    the question answer
     */
    public void insertFaq(long courseId, String courseName, String courseDescription, String courseLanguage, long faqId, String questionTitle, String questionAnswer) {

        var collection = getCollection(WeaviateSchemas.FAQS_COLLECTION);

        collection.data()
                .insert(Map.of(WeaviateSchemas.FaqsProperties.COURSE_ID, courseId, WeaviateSchemas.FaqsProperties.COURSE_NAME, courseName,
                        WeaviateSchemas.FaqsProperties.COURSE_DESCRIPTION, courseDescription, WeaviateSchemas.FaqsProperties.COURSE_LANGUAGE, courseLanguage,
                        WeaviateSchemas.FaqsProperties.FAQ_ID, faqId, WeaviateSchemas.FaqsProperties.QUESTION_TITLE, questionTitle, WeaviateSchemas.FaqsProperties.QUESTION_ANSWER,
                        questionAnswer));

        log.debug("Inserted FAQ {} for course {}", faqId, courseId);
    }

    /**
     * Checks if the Weaviate connection is healthy.
     *
     * @return true if the connection is healthy
     */
    public boolean isHealthy() {
        try {
            // Check if we can list collections
            client.collections().listAll();
            return true;
        }
        catch (Exception e) {
            log.warn("Weaviate health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Exception thrown when Weaviate operations fail.
     */
    public static class WeaviateException extends RuntimeException {

        public WeaviateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
