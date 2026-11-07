package de.tum.cit.aet.artemis.quiz.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.multipart.MultipartFile;

import com.tngtech.archunit.core.domain.JavaClass;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.models.Components;

/**
 * Keeps the committed OpenAPI specification in step with the quiz controllers and their DTOs.
 * <p>
 * The Angular client in {@code src/main/webapp/app/openapi} is generated from {@code config/openapi/openapi.yaml}, which is generated from a
 * running server and committed. A quiz endpoint or DTO changed without regenerating the file leaves the client stale, and nothing fails at
 * compile time. This test does not start a server: it reads the controllers with reflection and describes their bodies with swagger-core,
 * the library springdoc uses, then applies what {@code OpenAPIConfiguration#schemaCustomizer} and the {@code pruneOpenApiSpec} Gradle task
 * change in the generated file.
 */
class QuizOpenApiSpecificationArchitectureTest extends AbstractArchitectureTest {

    private static final Path SPECIFICATION = Path.of("config", "openapi", "openapi.yaml");

    private static final Path OPENAPI_GRADLE_SCRIPT = Path.of("gradle", "openapi.gradle");

    private static final String PATH_PREFIX = "/api/quiz/";

    private static final String SCHEMA_REFERENCE_PREFIX = "#/components/schemas/";

    private static final String DTO_SUFFIX = "DTO";

    private static final List<String> HTTP_METHODS = List.of("get", "put", "post", "patch", "delete");

    private static final Map<String, Class<?>> TYPES_WITHOUT_FORMAT = Map.of("integer", BigInteger.class, "number", BigDecimal.class, "string", String.class, "boolean",
            Boolean.class);

    private static final String REGENERATE = "Regenerate with './gradlew generateOpenApiDocs pruneOpenApiSpec -x webapp' and './gradlew openApiGenerate -x webapp', "
            + "then commit config/openapi/openapi.yaml and src/main/webapp/app/openapi.";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private static JsonNode specification;

    private static List<Endpoint> endpoints;

    /**
     * The DTO schemas that swagger-core describes for the bodies of the quiz endpoints, by their name in the specification.
     */
    private static Map<String, JsonNode> generatedSchemas;

    @BeforeAll
    static void describeTheQuizEndpoints() throws IOException {
        specification = YAMLMapper.builder().build().readTree(SPECIFICATION.toFile());
        generatedSchemas = new HashMap<>();
        List<String> excludedPaths = excludedPaths();
        AntPathMatcher pathMatcher = new AntPathMatcher();
        endpoints = productionClasses.stream().filter(javaClass -> javaClass.getPackageName().equals(ARTEMIS_PACKAGE + ".quiz.web"))
                .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class)).map(JavaClass::reflect).flatMap(controller -> endpointsOf(controller).stream())
                .filter(endpoint -> endpoint.path().startsWith(PATH_PREFIX) && excludedPaths.stream().noneMatch(excluded -> pathMatcher.match(excluded, endpoint.path()))).toList();
        flattenDiscriminatorBackReferences(generatedSchemas);
        assertThat(endpoints).as("quiz endpoints").isNotEmpty();
    }

    /**
     * Fails when an endpoint was added, removed, renamed or moved, or now takes or returns a different DTO, without regenerating.
     */
    @Test
    void theSpecificationListsEveryQuizEndpoint() {
        Set<String> documented = new TreeSet<>();
        for (var path : specification.path("paths").properties()) {
            if (path.getKey().startsWith(PATH_PREFIX)) {
                for (String httpMethod : HTTP_METHODS) {
                    JsonNode operation = path.getValue().path(httpMethod);
                    if (operation.isObject()) {
                        Set<String> references = new TreeSet<>();
                        collectSchemaReferences(operation, references);
                        // springdoc names an operation after its handler method and appends _1, _2 to repeated names.
                        String handler = operation.path("operationId").asString("").replaceAll("_\\d+$", "");
                        documented.add(httpMethod.toUpperCase(Locale.ROOT) + " " + path.getKey() + " " + handler + " uses " + references);
                    }
                }
            }
        }
        Set<String> mapped = new TreeSet<>(endpoints.stream().map(Endpoint::toString).toList());

        // "Not found" lists endpoints the specification lacks, "not expected" operations it lists that no longer exist or have changed.
        assertThat(documented).as("Operations under " + PATH_PREFIX + " in " + SPECIFICATION + " against the quiz controllers. " + REGENERATE)
                .containsExactlyInAnyOrderElementsOf(mapped);
    }

    /**
     * Fails when a path or query parameter of a quiz endpoint was added, removed, renamed, reordered or changed without regenerating.
     */
    @Test
    void theSpecificationDescribesTheParametersOfEveryQuizEndpoint() {
        Map<String, JsonNode> documented = new TreeMap<>();
        for (var path : specification.path("paths").properties()) {
            if (path.getKey().startsWith(PATH_PREFIX)) {
                for (String httpMethod : HTTP_METHODS) {
                    JsonNode operation = path.getValue().path(httpMethod);
                    if (operation.isObject()) {
                        documented.put(httpMethod.toUpperCase(Locale.ROOT) + " " + path.getKey(),
                                operation.path("parameters").isArray() ? operation.get("parameters") : JSON_MAPPER.createArrayNode());
                    }
                }
            }
        }

        List<String> differences = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            JsonNode documentedParameters = documented.get(endpoint.httpMethod() + " " + endpoint.path());
            // A missing operation is reported by theSpecificationListsEveryQuizEndpoint.
            if (documentedParameters != null && !documentedParameters.equals(endpoint.parameters())) {
                differences.add(endpoint.httpMethod() + " " + endpoint.path() + ". Specification: " + documentedParameters + " Controller: " + endpoint.parameters());
            }
        }
        assertThat(differences).as("Parameters of the operations under " + PATH_PREFIX + " in " + SPECIFICATION + " against the quiz controllers. " + REGENERATE).isEmpty();
    }

    /**
     * Fails when a DTO that a quiz endpoint takes or returns, directly or through another DTO, changed without regenerating.
     */
    @Test
    void theSpecificationDescribesEveryQuizDto() {
        JsonNode committedSchemas = specification.path("components").path("schemas");
        Map<String, JsonNode> committed = new TreeMap<>();
        Set<String> committedRoots = new TreeSet<>();
        for (var path : specification.path("paths").properties()) {
            if (path.getKey().startsWith(PATH_PREFIX)) {
                collectSchemaReferences(path.getValue(), committedRoots);
            }
        }
        reachableSchemas(committedRoots, name -> committedSchemas.get(name), committed);

        Map<String, JsonNode> generated = new TreeMap<>();
        Set<String> generatedRoots = new TreeSet<>();
        endpoints.forEach(endpoint -> generatedRoots.addAll(endpoint.schemaReferences()));
        reachableSchemas(generatedRoots, generatedSchemas::get, generated);

        List<String> differences = new ArrayList<>();
        Set<String> names = new TreeSet<>(committed.keySet());
        names.addAll(generated.keySet());
        for (String name : names) {
            if (!committed.containsKey(name)) {
                differences.add(name + " is missing from the specification");
            }
            else if (!generated.containsKey(name)) {
                differences.add(name + " is in the specification, but no quiz endpoint uses it");
            }
            else if (!committed.get(name).equals(generated.get(name))) {
                differences.add(name + " differs. Specification: " + committed.get(name) + " DTO: " + generated.get(name));
            }
        }
        assertThat(differences).as("Schemas of the operations under " + PATH_PREFIX + " in " + SPECIFICATION + " against the quiz DTOs. " + REGENERATE).isEmpty();
    }

    private static void reachableSchemas(Set<String> roots, Function<String, JsonNode> schemas, Map<String, JsonNode> reachable) {
        List<String> pending = new ArrayList<>(roots);
        while (!pending.isEmpty()) {
            String name = pending.removeLast();
            JsonNode schema = schemas.apply(name);
            if (schema != null && reachable.putIfAbsent(name, schema) == null) {
                Set<String> nested = new TreeSet<>();
                collectSchemaReferences(schema, nested);
                pending.addAll(nested);
            }
        }
    }

    private static void collectSchemaReferences(JsonNode node, Set<String> references) {
        String reference = node.path("$ref").asString("");
        if (reference.startsWith(SCHEMA_REFERENCE_PREFIX)) {
            references.add(reference.substring(SCHEMA_REFERENCE_PREFIX.length()));
        }
        node.values().forEach(child -> collectSchemaReferences(child, references));
    }

    /**
     * The paths that gradle/openapi.gradle keeps out of the specification with springdoc.paths-to-exclude: the legacy URL aliases.
     */
    private static List<String> excludedPaths() throws IOException {
        Matcher matcher = Pattern.compile("-Dspringdoc\\.paths-to-exclude=([^\"]+)\"").matcher(Files.readString(OPENAPI_GRADLE_SCRIPT));
        assertThat(matcher.find()).as("springdoc.paths-to-exclude in " + OPENAPI_GRADLE_SCRIPT).isTrue();
        return Arrays.asList(matcher.group(1).split(","));
    }

    private static List<Endpoint> endpointsOf(Class<?> controller) {
        RequestMapping controllerMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        String[] prefixes = controllerMapping == null || controllerMapping.path().length == 0 ? new String[] { "" } : controllerMapping.path();
        List<Endpoint> endpoints = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            Operation operation = AnnotatedElementUtils.findMergedAnnotation(method, Operation.class);
            if (mapping == null || method.isAnnotationPresent(Hidden.class) || (operation != null && operation.hidden())) {
                continue;
            }
            Set<String> schemaReferences = new TreeSet<>();
            bodyTypes(method).forEach(bodyType -> schemaReferences.addAll(describe(bodyType)));
            String[] paths = mapping.path().length == 0 ? new String[] { "" } : mapping.path();
            for (String prefix : prefixes) {
                for (String path : paths) {
                    for (RequestMethod requestMethod : mapping.method()) {
                        endpoints.add(new Endpoint(requestMethod.name(), normalized(prefix, path), method.getName(), schemaReferences, parametersOf(method)));
                    }
                }
            }
        }
        return endpoints;
    }

    private static String normalized(String prefix, String path) {
        // A path variable with a pattern, {name:regex}, is documented as {name}.
        return ("/" + prefix + "/" + path).replaceAll("/+", "/").replaceAll("\\{([^}:]+):[^}]*}", "{$1}").replaceAll("(.)/$", "$1");
    }

    /**
     * Describes the path and query parameters of a handler like springdoc: with swagger-core's ParameterProcessor, from the method
     * parameter and its annotations. A parameter object contributes its fields, and springdoc replaces Spring's Pageable with its own.
     */
    private static ArrayNode parametersOf(Method method) {
        ArrayNode parameters = JSON_MAPPER.createArrayNode();
        for (Parameter parameter : method.getParameters()) {
            PathVariable pathVariable = AnnotatedElementUtils.findMergedAnnotation(parameter, PathVariable.class);
            RequestParam requestParam = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestParam.class);
            if (pathVariable != null) {
                parameters.add(describeParameter(nameOf(pathVariable.name(), parameter), "path", true, ValueConstants.DEFAULT_NONE, parameter.getParameterizedType(),
                        parameter.getAnnotations()));
            }
            else if (requestParam != null) {
                boolean required = requestParam.required() && ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue()) && parameter.getType() != Optional.class;
                parameters.add(describeParameter(nameOf(requestParam.name(), parameter), "query", required, requestParam.defaultValue(), parameter.getParameterizedType(),
                        parameter.getAnnotations()));
            }
            else if (parameter.isAnnotationPresent(ParameterObject.class)) {
                Class<?> parameterObject = parameter.getType() == Pageable.class ? org.springdoc.core.converters.models.Pageable.class : parameter.getType();
                for (Field field : parameterObject.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        parameters.add(describeParameter(field.getName(), "query", false, ValueConstants.DEFAULT_NONE, field.getGenericType(), field.getAnnotations()));
                    }
                }
            }
        }
        return parameters;
    }

    private static String nameOf(String declaredName, Parameter parameter) {
        if (!declaredName.isEmpty()) {
            return declaredName;
        }
        assertThat(parameter.isNamePresent()).as("the name of " + parameter + ", which needs the -parameters compiler flag").isTrue();
        return parameter.getName();
    }

    private static JsonNode describeParameter(String name, String in, boolean required, String defaultValue, Type type, Annotation[] annotations) {
        var parameter = new io.swagger.v3.oas.models.parameters.Parameter().name(name).in(in).required(required);
        ParameterProcessor.applyAnnotations(parameter, documentedType(type, annotations), Arrays.asList(annotations), new Components(), new String[0], new String[0], null, true);
        if (!ValueConstants.DEFAULT_NONE.equals(defaultValue)) {
            parameter.getSchema().setDefault(defaultValue);
        }
        return JSON_MAPPER.readTree(Json31.pretty(parameter));
    }

    /**
     * An explicit type in {@code @Parameter(schema = @Schema(type = ...))} replaces the Java type in the specification: its format and enum
     * values are not documented. A Java type without format or enum values stands in for it.
     */
    private static Type documentedType(Type type, Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof io.swagger.v3.oas.annotations.Parameter documentation && !documentation.schema().type().isEmpty()) {
                String schemaType = documentation.schema().type();
                assertThat(TYPES_WITHOUT_FORMAT).as("a Java type for the parameter schema type " + schemaType).containsKey(schemaType);
                return TYPES_WITHOUT_FORMAT.get(schemaType);
            }
        }
        return type;
    }

    /**
     * The types that springdoc describes with schemas: the request body or the request parts that are not files, and the response body.
     */
    private static List<Type> bodyTypes(Method method) {
        List<Type> types = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            boolean isBody = parameter.isAnnotationPresent(RequestBody.class) || parameter.isAnnotationPresent(RequestPart.class);
            if (isBody && !isFile(parameter.getParameterizedType())) {
                types.add(parameter.getParameterizedType());
            }
        }
        // The handlers are not generic, so the declared return type is concrete.
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw && HttpEntity.class.isAssignableFrom(raw)) {
            returnType = parameterized.getActualTypeArguments()[0];
        }
        if (returnType != void.class && returnType != Void.class) {
            types.add(returnType);
        }
        return types;
    }

    private static boolean isFile(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            return Arrays.stream(parameterized.getActualTypeArguments()).anyMatch(QuizOpenApiSpecificationArchitectureTest::isFile);
        }
        return type instanceof Class<?> raw && MultipartFile.class.isAssignableFrom(raw);
    }

    /**
     * Describes a body type like springdoc, adds the DTO schemas it uses to {@link #generatedSchemas} and returns the names of the DTO
     * schemas it references directly.
     */
    private static Set<String> describe(Type bodyType) {
        ResolvedSchema resolved = ModelConverters.getInstance(true).resolveAsResolvedSchema(new AnnotatedType(bodyType).resolveAsRef(true));
        resolved.referencedSchemas.forEach((name, schema) -> {
            if (name.endsWith(DTO_SUFFIX)) {
                generatedSchemas.put(withoutDtoSuffix(name), withSpecificationReferences(JSON_MAPPER.readTree(Json31.pretty(schema))));
            }
        });
        Set<String> references = new TreeSet<>();
        if (resolved.schema != null) {
            collectSchemaReferences(withSpecificationReferences(JSON_MAPPER.readTree(Json31.pretty(resolved.schema))), references);
        }
        return references;
    }

    /**
     * Applies what OpenAPIConfiguration#schemaCustomizer and pruneOpenApiSpec do to references: only DTO schemas are kept, under their name
     * without the suffix, so a reference to a DTO loses the suffix and a reference to anything else is removed.
     */
    private static JsonNode withSpecificationReferences(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = JSON_MAPPER.createObjectNode();
            for (var property : node.properties()) {
                if (!isReferenceToRemovedSchema(property.getValue())) {
                    object.set(property.getKey(), withSpecificationReferences(property.getValue()));
                }
            }
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = JSON_MAPPER.createArrayNode();
            for (JsonNode element : node) {
                if (!isReferenceToRemovedSchema(element)) {
                    array.add(withSpecificationReferences(element));
                }
            }
            return array;
        }
        if (node.isString() && node.asString().startsWith(SCHEMA_REFERENCE_PREFIX) && node.asString().endsWith(DTO_SUFFIX)) {
            return JSON_MAPPER.getNodeFactory().stringNode(withoutDtoSuffix(node.asString()));
        }
        return node;
    }

    private static boolean isReferenceToRemovedSchema(JsonNode node) {
        String reference = node.path("$ref").asString("");
        return reference.startsWith(SCHEMA_REFERENCE_PREFIX) && !reference.endsWith(DTO_SUFFIX);
    }

    /**
     * Applies what pruneOpenApiSpec does for the generated client: a subtype of a discriminated oneOf loses its allOf reference back to the
     * parent, and a single remaining allOf entry is inlined.
     */
    private static void flattenDiscriminatorBackReferences(Map<String, JsonNode> schemas) {
        schemas.forEach((parentName, parent) -> {
            if (!parent.has("oneOf") || !parent.has("discriminator")) {
                return;
            }
            String parentReference = SCHEMA_REFERENCE_PREFIX + parentName;
            for (JsonNode option : parent.get("oneOf")) {
                JsonNode subtype = schemas.get(option.path("$ref").asString("").replace(SCHEMA_REFERENCE_PREFIX, ""));
                if (subtype instanceof ObjectNode subtypeObject && subtype.get("allOf") instanceof ArrayNode allOf) {
                    allOf.removeIf(entry -> parentReference.equals(entry.path("$ref").asString("")));
                    if (allOf.size() == 1 && allOf.get(0) instanceof ObjectNode remaining) {
                        subtypeObject.remove("allOf");
                        remaining.properties().forEach(entry -> {
                            if (!subtypeObject.has(entry.getKey())) {
                                subtypeObject.set(entry.getKey(), entry.getValue());
                            }
                        });
                    }
                }
            }
        });
    }

    private static String withoutDtoSuffix(String name) {
        return name.substring(0, name.length() - DTO_SUFFIX.length());
    }

    /**
     * A handler method mapped to one HTTP method and path, with the DTO schemas its bodies reference directly.
     */
    private record Endpoint(String httpMethod, String path, String handler, Set<String> schemaReferences, JsonNode parameters) {

        @Override
        public String toString() {
            return httpMethod + " " + path + " " + handler + " uses " + schemaReferences;
        }
    }
}
