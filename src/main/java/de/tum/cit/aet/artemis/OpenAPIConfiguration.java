package de.tum.cit.aet.artemis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * Configuration class for OpenAPI Spec customization.
 * <p>
 * This class customizes the OpenAPI schema to:
 * - Remove "DTO" suffixes from schema names and properties.
 * - Adjust response schemas to use a binary format for CSV responses.
 * - Set application metadata such as title, version, and contact information.
 */
@Configuration
@Profile(PROFILE_CORE)
@Lazy
public class OpenAPIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIConfiguration.class);

    private static final String OK_STATUS_CODE = "200";

    private static final int RESOURCE_NUMBER_OF_CHARACTERS = 9;

    private static final int DTO_NUMBER_OF_CHARACTERS = 3;

    @Value("${artemis.version}")
    private String version;

    /**
     * Creates an {@link OpenApiCustomizer} that applies a series of schema and operation name transformations
     * to the generated OpenAPI definition for the Artemis Application Server API.
     *
     * <p>
     * The customizer will:
     * </p>
     * <ul>
     * <li>Set the API title, version, and contact information on the OpenAPI {@link Info} object.</li>
     * <li>Filter component schemas to only include those ending in “Dto”, strip the “Dto” suffix
     * from their schema names, and remove the “Dto” suffix from all property names within those schemas.</li>
     * <li>Iterate over all paths and operations to:
     * <ul>
     * <li>Remove any trailing underscore plus digit characters from operation IDs.</li>
     * <li>Remove the “Dto” suffix from any response schemas.</li>
     * <li>Remove the “Dto” suffix from any request-body schemas, if present.</li>
     * <li>Remove any resource-name suffix from operation tags.</li>
     * </ul>
     * </li>
     * </ul>
     *
     * @return an {@link OpenApiCustomizer} that will apply the above transformations
     */
    @Bean
    public OpenApiCustomizer schemaCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            openApi.info(new Info().title("Artemis Application Server API").version(version).contact(new Contact().email("krusche@tum.de").name("Stephan Krusche")));

            if (components != null && components.getSchemas() != null) {
                Map<String, Schema> schemas = filterForSchemasWithDtoSuffixAndStripSuffix(components);
                removeDtoSuffixFromAttributeNames(schemas);

                components.setSchemas(schemas);
            }
            else {
                log.warn("Components or Schemas are null in OpenAPI configuration.");
            }

            Paths paths = openApi.getPaths();
            if (paths == null) {
                log.warn("Paths are null in OpenAPI configuration.");
                return;
            }
            paths.forEach((path, pathItem) -> {
                pathItem.readOperations().forEach(operation -> {
                    stripTrailingUnderscoreDigitCharacter(operation);
                    removeDtoSuffixFromResponseSchemas(operation);
                    removeDtoSuffixFromRequestBodyIfExisting(operation);

                    removeResourceSuffixFromTags(operation);
                });
            });
        };
    }

    private static void stripTrailingUnderscoreDigitCharacter(Operation operation) {
        String id = operation.getOperationId();
        operation.setOperationId(id.replaceAll("_\\d+$", ""));
    }

    private void removeDtoSuffixFromResponseSchemas(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return;
        }
        responses.forEach((responseCode, response) -> {
            Content content = response.getContent();
            if (content == null) {
                log.warn("Response with code {} has no content.", responseCode);
                return;
            }
            content.forEach((contentType, mediaType) -> {
                if (mediaType != null && mediaType.getSchema() != null) {
                    removeDTOSuffixesFromSchemaRecursively(mediaType.getSchema());
                }
                else {
                    log.warn("MediaType or Schema is null for content type: {}", contentType);
                }
            });
        });
    }

    private void removeDtoSuffixFromAttributeNames(Map<String, Schema> schemas) {
        schemas.forEach((key, value) -> {
            @SuppressWarnings("unchecked")
            Map<String, Schema<?>> properties = value.getProperties();

            if (properties == null) {
                return;
            }
            properties.forEach((propertyKey, propertyValue) -> {
                removeDTOSuffixesFromSchemaRecursively(propertyValue);
            });
        });
    }

    private static Map<String, Schema> filterForSchemasWithDtoSuffixAndStripSuffix(Components components) {
        return components.getSchemas().entrySet().stream().filter(entry -> entry.getKey().endsWith("DTO"))
                .collect(Collectors.toMap(entry -> entry.getKey().substring(0, entry.getKey().length() - DTO_NUMBER_OF_CHARACTERS), entry -> {
                    Schema<?> schema = entry.getValue();
                    schema.setName(entry.getKey().substring(0, entry.getKey().length() - DTO_NUMBER_OF_CHARACTERS));
                    return schema;
                }));
    }

    private void removeDtoSuffixFromRequestBodyIfExisting(Operation operation) {
        if (operation.getRequestBody() != null) {
            var requestBodyContent = operation.getRequestBody().getContent();
            requestBodyContent.forEach((contentType, mediaType) -> {
                removeDTOSuffixesFromSchemaRecursively(mediaType.getSchema());
            });
        }
    }

    private static void removeResourceSuffixFromTags(Operation operation) {
        if (operation.getTags() != null) {
            operation.setTags(operation.getTags().stream().filter(tag -> {
                if (tag.length() > RESOURCE_NUMBER_OF_CHARACTERS) {
                    return true;
                }
                else {
                    log.warn("Tag '{}' is shorter than expected and cannot be trimmed.", tag);
                    return false;
                }
            }).map(tag -> tag.substring(0, tag.length() - RESOURCE_NUMBER_OF_CHARACTERS)).toList());
        }
    }

    private void removeDTOSuffixesFromSchemaRecursively(Schema<?> schema) {
        if (schema == null) {
            return;
        }

        if (schema.get$ref() != null && schema.get$ref().endsWith("DTO")) {
            String newRef = schema.get$ref().substring(0, schema.get$ref().length() - DTO_NUMBER_OF_CHARACTERS);
            schema.set$ref(newRef);
            log.debug("Updated $ref from {} to {}", schema.get$ref(), newRef);
        }

        if (schema.getItems() != null) {
            removeDTOSuffixesFromSchemaRecursively(schema.getItems());
        }
    }

    /**
     * Registers an OpenApiCustomizer that adjusts the schema for all CSV endpoints
     * in the generated OpenAPI specification.
     *
     * <p>
     * <strong>Default behavior:</strong> SpringDoc will document a response
     * producing "text/csv" as a plain string schema (i.e. {@code type="string"}
     * with no specific {@code format}). Client generators typically interpret
     * this as regular text, which may not trigger file-download behavior.
     * </p>
     *
     * <p>
     * <strong>Why this customizer is needed:</strong> By setting
     * {@code format="binary"} on the schema, we signal to OpenAPI tools and
     * generated clients that the CSV payload should be handled as a downloadable
     * binary stream (e.g. saving to file), rather than as inline text.
     * </p>
     *
     * @return an {@link OpenApiCustomizer} bean that finds every operation’s
     *         200 response with media type "text/csv" and replaces its schema
     *         with a binary string schema ({@code type="string"},
     *         {@code format="binary"}).
     */
    @Bean
    public OpenApiCustomizer binaryFormatCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(op -> {
            ApiResponses responses = op.getResponses();
            ApiResponse okResponse = responses.get(OK_STATUS_CODE);
            if (okResponse == null) {
                return;
            }
            if (okResponse.getContent() == null) {
                return;
            }
            MediaType media = okResponse.getContent().get("text/csv");
            if (media == null) {
                return;
            }
            // replace schema with a binary string so clients treat CSV as a file download
            media.setSchema(new StringSchema().type("string").format("binary"));
        }));
    }

}
