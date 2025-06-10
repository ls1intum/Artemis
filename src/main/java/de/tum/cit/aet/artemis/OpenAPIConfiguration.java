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

@Configuration
@Profile(PROFILE_CORE)
public class OpenAPIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIConfiguration.class);

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
                        .collect(Collectors.toMap(entry -> entry.getKey().substring(0, entry.getKey().length() - 3), entry -> {
                            var schema = entry.getValue();
                            schema.setName(entry.getKey().substring(0, entry.getKey().length() - 3));
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

                        // Remove -controller suffix from tags
                        if (operation.getTags() != null) {
                            operation.setTags(operation.getTags().stream().filter(tag -> {
                                if (tag.length() > 11) {
                                    return true;
                                }
                                else {
                                    log.warn("Tag '{}' is shorter than expected and cannot be trimmed.", tag);
                                    return false;
                                }
                            }).map(tag -> tag.substring(0, tag.length() - 9)).toList());
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
            String newRef = schema.get$ref().substring(0, schema.get$ref().length() - 3);
            schema.set$ref(newRef);
            log.debug("Updated $ref from {} to {}", schema.get$ref(), newRef);
        }

        if (schema.getItems() != null) {
            removeDTOSuffixesFromSchemaRecursively(schema.getItems());
        }
    }

    @Bean
    public OpenApiCustomizer binaryFormatCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(op -> {
            ApiResponses responses = op.getResponses();
            ApiResponse resp200 = responses.get("200");
            if (resp200 != null && resp200.getContent() != null) {
                MediaType media = resp200.getContent().get("text/csv");
                if (media != null) {
                    // replace schema with binary string
                    media.setSchema(new StringSchema().type("string").format("binary"));
                }
            }
        }));
    }
}
