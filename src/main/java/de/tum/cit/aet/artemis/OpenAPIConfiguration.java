package de.tum.cit.aet.artemis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
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
public class OpenAPIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIConfiguration.class);

    private static final String OK_STATUS_CODE = "200";

    private static final int RESOURCE_NUMBER_OF_CHARACTERS = 9;

    private static final int DTO_NUMBER_OF_CHARACTERS = 3;

    @Value("${artemis.version}")
    private String version;

    @Bean
    public OpenApiCustomizer schemaCustomizer() {
        return openApi -> {
            var components = openApi.getComponents();
            openApi.info(new Info().title("Artemis Application Server API").version(version).contact(new Contact().email("krusche@tum.de").name("Stephan Krusche")));

            if (components != null && components.getSchemas() != null) {
                // Only include schemas with DTO suffix and remove the suffix
                var schemas = components.getSchemas().entrySet().stream().filter(entry -> entry.getKey().endsWith("DTO"))
                        .collect(Collectors.toMap(entry -> entry.getKey().substring(0, entry.getKey().length() - DTO_NUMBER_OF_CHARACTERS), entry -> {
                            var schema = entry.getValue();
                            schema.setName(entry.getKey().substring(0, entry.getKey().length() - DTO_NUMBER_OF_CHARACTERS));
                            return schema;
                        }));

                // Remove DTO suffix from attribute names
                schemas.forEach((key, value) -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Schema<?>> properties = value.getProperties();
                    if (properties != null) {
                        properties.forEach((propertyKey, propertyValue) -> {
                            removeDTOSuffixesFromSchemaRecursively(propertyValue);
                        });
                    }
                });

                components.setSchemas(schemas);
                if (!components.getSchemas().containsKey("EmptyResponse")) {
                    Schema<?> emptySchema = new Schema<>().type("object").description("An empty response (no content)").properties(Collections.emptyMap());
                    components.addSchemas("EmptyResponse", emptySchema);
                }
            }
            else {
                log.warn("Components or Schemas are null in OpenAPI configuration.");
            }

            var paths = openApi.getPaths();
            if (paths != null) {
                paths.forEach((path, pathItem) -> {
                    log.info("Processing path: {}", path);
                    pathItem.readOperations().forEach(operation -> {
                        String id = operation.getOperationId();
                        // strip any trailing "_<digit>" suffix
                        operation.setOperationId(id.replaceAll("_\\d+$", "")); // Remove DTO suffix from response schemas
                        var responses = operation.getResponses();
                        if (responses != null) {
                            responses.forEach((responseCode, response) -> {
                                var content = response.getContent();
                                if (content != null) {
                                    content.forEach((contentType, mediaType) -> {
                                        if (mediaType != null && mediaType.getSchema() != null) {
                                            removeDTOSuffixesFromSchemaRecursively(mediaType.getSchema());
                                        }
                                        else {
                                            log.warn("MediaType or Schema is null for content type: {}", contentType);
                                        }
                                    });
                                }
                                else {
                                    log.warn("Response with code {} has no content.", responseCode);
                                }
                            });
                        }
                        if (operation.getRequestBody() != null) {
                            var requestBodyContent = operation.getRequestBody().getContent();
                            requestBodyContent.forEach((contentType, mediaType) -> {
                                removeDTOSuffixesFromSchemaRecursively(mediaType.getSchema());
                            });
                        }

                        // Remove -resource suffix from tags
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
                    });
                });
            }
            else {
                log.warn("Paths are null in OpenAPI configuration.");
            }
        };
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
            ApiResponse resp200 = responses.get(OK_STATUS_CODE);
            if (resp200 != null && resp200.getContent() != null) {
                MediaType media = resp200.getContent().get("text/csv");
                if (media != null) {
                    // replace schema with a binary string so clients treat CSV as a file download
                    media.setSchema(new StringSchema().type("string").format("binary"));
                }
            }
        }));
    }

}
