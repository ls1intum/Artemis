package de.tum.cit.aet.artemis.core.config.weaviate.schema;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.weaviate.WeaviateConfigurationProperties;

/**
 * Fetches Weaviate schema definitions from the Iris GitHub repository.
 * This component parses the Python schema files from Iris to extract
 * property definitions for validation against the local Artemis schemas.
 */
@Component
@ConditionalOnProperty(name = "artemis.weaviate.enabled", havingValue = "true")
public class IrisSchemaFetcher {

    private static final Logger log = LoggerFactory.getLogger(IrisSchemaFetcher.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Mapping of schema file names to collection names.
     */
    private static final Map<String, String> SCHEMA_FILES = Map.of("lecture_unit_page_chunk_schema.py", WeaviateSchemas.LECTURES_COLLECTION, "lecture_transcription_schema.py",
            WeaviateSchemas.LECTURE_TRANSCRIPTIONS_COLLECTION, "lecture_unit_segment_schema.py", WeaviateSchemas.LECTURE_UNIT_SEGMENTS_COLLECTION, "lecture_unit_schema.py",
            WeaviateSchemas.LECTURE_UNITS_COLLECTION, "faq_schema.py", WeaviateSchemas.FAQS_COLLECTION);

    private final WeaviateConfigurationProperties properties;

    private final HttpClient httpClient;

    public IrisSchemaFetcher(WeaviateConfigurationProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }

    /**
     * Fetches all Iris schema definitions from GitHub.
     *
     * @return map of collection name to parsed schema
     * @throws IrisSchemaFetchException if fetching or parsing fails
     */
    public Map<String, IrisSchemaDefinition> fetchAllSchemas() throws IrisSchemaFetchException {
        Map<String, IrisSchemaDefinition> schemas = new HashMap<>();

        for (Map.Entry<String, String> entry : SCHEMA_FILES.entrySet()) {
            String fileName = entry.getKey();
            String collectionName = entry.getValue();

            try {
                String content = fetchSchemaFile(fileName);
                IrisSchemaDefinition schema = parseSchemaFile(content, collectionName);
                schemas.put(collectionName, schema);
                log.debug("Successfully fetched and parsed Iris schema for collection: {}", collectionName);
            }
            catch (Exception e) {
                throw new IrisSchemaFetchException("Failed to fetch or parse Iris schema file: " + fileName, e);
            }
        }

        return schemas;
    }

    /**
     * Fetches a single schema file from GitHub.
     *
     * @param fileName the schema file name
     * @return the file content
     * @throws IOException          if the request fails
     * @throws InterruptedException if the request is interrupted
     */
    private String fetchSchemaFile(String fileName) throws IOException, InterruptedException {
        String baseUrl = properties.getSchemaValidation().getIrisSchemaBaseUrl();
        String url = baseUrl + "/" + fileName;

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch schema file " + fileName + ": HTTP " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Parses a Python schema file to extract property definitions.
     *
     * @param content        the file content
     * @param collectionName the expected collection name
     * @return the parsed schema definition
     */
    private IrisSchemaDefinition parseSchemaFile(String content, String collectionName) {
        List<IrisPropertyDefinition> properties = new ArrayList<>();

        // Parse Property definitions from the Python code
        // Pattern matches: Property(name="...", data_type=DataType.XXX, ...)
        Pattern propertyPattern = Pattern.compile("Property\\s*\\(\\s*name\\s*=\\s*[\"']([^\"']+)[\"']\\s*," + "\\s*data_type\\s*=\\s*DataType\\.([A-Z]+)"
                + "(?:.*?index_searchable\\s*=\\s*(True|False))?" + "(?:.*?index_filterable\\s*=\\s*(True|False))?", Pattern.DOTALL);

        Matcher matcher = propertyPattern.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            String dataType = matcher.group(2).toLowerCase();
            boolean indexSearchable = "True".equals(matcher.group(3));
            boolean indexFilterable = "True".equals(matcher.group(4));

            properties.add(new IrisPropertyDefinition(name, dataType, indexSearchable, indexFilterable));
        }

        // Also try to extract properties defined via enum constants
        // Pattern: PROPERTY_NAME = "property_name"
        Pattern enumPattern = Pattern.compile("([A-Z_]+)\\s*=\\s*[\"']([a-z_]+)[\"']");
        Matcher enumMatcher = enumPattern.matcher(content);
        Map<String, String> enumConstants = new HashMap<>();
        while (enumMatcher.find()) {
            enumConstants.put(enumMatcher.group(1), enumMatcher.group(2));
        }

        // Parse references if present
        List<IrisReferenceDefinition> references = new ArrayList<>();
        Pattern refPattern = Pattern.compile("ReferenceProperty\\s*\\(\\s*name\\s*=\\s*[\"']([^\"']+)[\"']\\s*," + "\\s*target_collection\\s*=\\s*[\"']([^\"']+)[\"']",
                Pattern.DOTALL);
        Matcher refMatcher = refPattern.matcher(content);
        while (refMatcher.find()) {
            references.add(new IrisReferenceDefinition(refMatcher.group(1), refMatcher.group(2)));
        }

        return new IrisSchemaDefinition(collectionName, properties, references);
    }

    /**
     * Exception thrown when fetching Iris schemas fails.
     */
    public static class IrisSchemaFetchException extends Exception {

        public IrisSchemaFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Represents a parsed Iris schema definition.
     */
    public record IrisSchemaDefinition(String collectionName, List<IrisPropertyDefinition> properties, List<IrisReferenceDefinition> references) {
    }

    /**
     * Represents a parsed Iris property definition.
     */
    public record IrisPropertyDefinition(String name, String dataType, boolean indexSearchable, boolean indexFilterable) {
    }

    /**
     * Represents a parsed Iris reference definition.
     */
    public record IrisReferenceDefinition(String name, String targetCollection) {
    }
}
