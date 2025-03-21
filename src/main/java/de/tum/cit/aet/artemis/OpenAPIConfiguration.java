package de.tum.cit.aet.artemis;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;

@Configuration
public class OpenAPIConfiguration {

    Logger logger = LoggerFactory.getLogger(OpenAPIConfiguration.class);

    @Bean
    public OpenApiCustomizer schemaCustomizer() {
        return openApi -> {
            var components = openApi.getComponents();
            openApi.info(new Info().title("Artemis Application Server API").version("8.0.0").contact(new Contact().email("krusche@tum.de").name("Stephan Krusche")));

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
            }
            else {
                logger.warn("Components or Schemas are null in OpenAPI configuration.");
            }

            var paths = openApi.getPaths();
            if (paths != null) {
                paths.forEach((path, pathItem) -> {
                    logger.info("Processing path: {}", path);
                    pathItem.readOperations().forEach(operation -> {
                        // Remove DTO suffix from response schemas
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
                                            logger.warn("MediaType or Schema is null for content type: {}", contentType);
                                        }
                                    });
                                }
                                else {
                                    logger.warn("Response with code {} has no content.", responseCode);
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
                                    logger.warn("Tag '{}' is shorter than expected and cannot be trimmed.", tag);
                                    return false;
                                }
                            }).map(tag -> tag.substring(0, tag.length() - 11)).collect(Collectors.toList()));
                        }
                    });
                });
            }
            else {
                logger.warn("Paths are null in OpenAPI configuration.");
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
            logger.debug("Updated $ref from {} to {}", schema.get$ref(), newRef);
        }

        if (schema.getItems() != null) {
            removeDTOSuffixesFromSchemaRecursively(schema.getItems());
        }
    }
}
