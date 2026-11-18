package de.tum.cit.aet.artemis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.security.allowedScopes.AllowedScopes;
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
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

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

    private static final String AGENT_API_GROUP = "agent";

    private static final String AGENT_SECURITY_SCHEME = "personalAccessToken";

    private static final String AGENT_API_VERSION = "1";

    private static final String AGENT_API_DESCRIPTION = """
            The part of the Artemis REST API that a personal access token can call. Create a token in User Settings under Agent Access \
            and send it as `Authorization: Bearer art_pat_...`. A token acts as its owner, with at most the owner's role in each course, \
            and only on operations whose scopes it holds. Exercises and lecture units created through this API get a release date in \
            the future, which publishes them on its own, so a person must review them before it. Announcements, lectures and competencies \
            are visible to students at once.""";

    @Value("${artemis.version}")
    private String version;

    /**
     * Creates an {@link OpenApiCustomizer} that applies a series of schema and tag name transformations
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
                    removeDtoSuffixFromResponseSchemas(operation);
                    removeDtoSuffixFromRequestBodyIfExisting(operation);

                    removeResourceSuffixFromTags(operation);
                });
            });
        };
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
            removeDTOSuffixesFromSchemaRecursively(value);
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

        if (schema.getAdditionalProperties() instanceof Schema<?> additionalPropertiesSchema) {
            removeDTOSuffixesFromSchemaRecursively(additionalPropertiesSchema);
        }

        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(this::removeDTOSuffixesFromSchemaRecursively);
        }

        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(this::removeDTOSuffixesFromSchemaRecursively);
        }

        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(this::removeDTOSuffixesFromSchemaRecursively);
        }

        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(this::removeDTOSuffixesFromSchemaRecursively);
        }

        if (schema.getDiscriminator() != null && schema.getDiscriminator().getMapping() != null) {
            schema.getDiscriminator().getMapping()
                    .replaceAll((key, value) -> value != null && value.startsWith("#/components/schemas/") && value.endsWith("DTO")
                            ? value.substring(0, value.length() - DTO_NUMBER_OF_CHARACTERS)
                            : value);
        }
    }

    /**
     * Registers the agent API, a second specification that lists only the endpoints a personal access token can call, that is those
     * annotated with {@link AllowedScopes}. It exists only while the specifications are generated, so a running server does not
     * serve it. It is deliberately not limited to the packages of the application specification, which feeds the generated client.
     *
     * @return the group for the agent API
     */
    @Bean
    @ConditionalOnProperty(name = "artemis.openapi-docs-generation", havingValue = "true")
    public GroupedOpenApi agentApi() {
        return GroupedOpenApi.builder().group(AGENT_API_GROUP).packagesToScan("de.tum.cit.aet.artemis")
                .addOpenApiMethodFilter(method -> method.isAnnotationPresent(AllowedScopes.class)).addOpenApiCustomizer(agentApiCustomizer()).build();
    }

    /**
     * Describes the agent API for the people and programs that read it: how to authenticate, what the scopes mean, which errors
     * every operation can return. It is applied only to the agent group, never to the application specification.
     */
    private OpenApiCustomizer agentApiCustomizer() {
        return openApi -> {
            // The version of this contract, not of Artemis: it would otherwise fall behind with every release, because the file is
            // published rather than bumped with the application version. Which operations exist depends on the instance's version.
            openApi.info(new Info().title("Artemis API for coding agents").version(AGENT_API_VERSION).description(AGENT_API_DESCRIPTION));
            openApi.servers(List.of(new Server().url("https://artemis.example.org").description("Replace with the address of your Artemis instance.")));
            if (openApi.getComponents() == null) {
                openApi.components(new Components());
            }
            openApi.getComponents().addSecuritySchemes(AGENT_SECURITY_SCHEME,
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").description("A personal access token, which starts with art_pat_."));
            openApi.addSecurityItem(new SecurityRequirement().addList(AGENT_SECURITY_SCHEME));
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                ApiResponses responses = operation.getResponses();
                responses.putIfAbsent("400", new ApiResponse()
                        .description("The request is invalid. The body is an RFC 7807 problem document with a message and, for validation errors, a list of fieldErrors."));
                responses.putIfAbsent("401", new ApiResponse().description("The token is missing, unknown, expired or revoked, or its owner is an administrator."));
                responses.putIfAbsent("403", new ApiResponse().description("The token lacks a scope this operation needs, or its owner lacks the required role in the course."));
                responses.putIfAbsent("404", new ApiResponse().description("The course or object does not exist, or is not visible to the token's owner."));
            }));
        };
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
