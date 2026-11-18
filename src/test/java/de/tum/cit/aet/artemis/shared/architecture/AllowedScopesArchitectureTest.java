package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AllowedScopes;
import io.swagger.v3.oas.annotations.Hidden;

/**
 * Keeps {@link AllowedScopes} a narrowing of access and never a replacement for the role check.
 * <p>
 * A personal access token authenticates as its owner, so an endpoint that opts in to tokens still has to decide, like any
 * other, which roles may call it. An {@code @AllowedScopes} endpoint without an {@code @EnforceAtLeast...} annotation would
 * be reachable by every authenticated user who holds the scope, whatever their role in the course.
 * <p>
 * The endpoints are also published as the agent API specification, which lists only DTOs: an endpoint that takes or returns a
 * domain object would silently drop out of it.
 */
class AllowedScopesArchitectureTest extends AbstractArchitectureTest {

    private static final String ENFORCE_ROLE_ANNOTATION_PREFIX = "EnforceAtLeast";

    private static final Path AGENT_SPECIFICATION = Path.of("documentation", "static", "agent", "openapi.yaml");

    private static final List<String> HTTP_METHODS = List.of("get", "put", "post", "patch", "delete");

    @Test
    void endpointsOptedInToTokensEnforceARole() {
        methods().that().areAnnotatedWith(AllowedScopes.class).should(haveAnEnforceAtLeastAnnotation()).check(productionClasses);
    }

    @Test
    void endpointsOptedInToTokensDoNotExposeDomainObjects() {
        methods().that().areAnnotatedWith(AllowedScopes.class).should(onlyUseDtosInTheirBodies()).check(productionClasses);
    }

    /**
     * The agent specification is generated, not written, and is committed so that it can be published. This fails when it no longer
     * matches the endpoints that opted in: an endpoint was opted in without regenerating, or one lacks the summary that tells an agent
     * what it does.
     */
    @Test
    void theAgentSpecificationListsEveryEndpointOptedInToTokens() throws IOException {
        // The MCP endpoint is hidden: it is a protocol, not an operation, and its tools are described by the protocol itself.
        List<String> optedIn = productionClasses.stream().flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(AllowedScopes.class) && !method.isAnnotatedWith(Hidden.class)).map(JavaMethod::getName).sorted().toList();

        JsonNode paths = YAMLMapper.builder().build().readTree(AGENT_SPECIFICATION.toFile()).path("paths");
        List<String> documented = new ArrayList<>();
        for (var path : paths.properties()) {
            for (String verb : HTTP_METHODS) {
                JsonNode operation = path.getValue().path(verb);
                if (operation.isObject()) {
                    assertThat(operation.path("summary").asString("")).as("summary of " + verb.toUpperCase(Locale.ROOT) + " " + path.getKey()).isNotBlank();
                    // springdoc names an operation after its handler method and appends _1, _2 to repeated names.
                    documented.add(operation.path("operationId").asString("").replaceAll("_\\d+$", ""));
                }
            }
        }
        documented.sort(null);

        // Comparing the names and not only the counts catches one endpoint opted in while another was removed.
        assertThat(documented).as("Operations in " + AGENT_SPECIFICATION + " against endpoints annotated with @AllowedScopes. Regenerate with "
                + "'./gradlew generateOpenApiDocs -PopenApiGroup=agent pruneOpenApiSpec -x webapp'.").isEqualTo(optedIn);
    }

    private static ArchCondition<JavaMethod> haveAnEnforceAtLeastAnnotation() {
        return new ArchCondition<>("be annotated with an @EnforceAtLeast... annotation") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean enforcesRole = method.getAnnotations().stream().map(JavaAnnotation::getRawType)
                        .anyMatch(annotationType -> annotationType.getSimpleName().startsWith(ENFORCE_ROLE_ANNOTATION_PREFIX));
                if (!enforcesRole) {
                    events.add(violated(method, method.getFullName() + " is annotated with @AllowedScopes but not with an @EnforceAtLeast... annotation"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> onlyUseDtosInTheirBodies() {
        return new ArchCondition<>("neither take a domain object as the request body nor return one") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method.getReturnType().getAllInvolvedRawTypes().stream().filter(AllowedScopesArchitectureTest::isDomainObject)
                        .forEach(type -> events.add(violated(method, method.getFullName() + " returns the domain object " + type.getName())));
                for (JavaParameter parameter : method.getParameters()) {
                    boolean isRequestBody = parameter.getAnnotations().stream().anyMatch(annotation -> annotation.getRawType().getSimpleName().equals("RequestBody"));
                    if (isRequestBody) {
                        parameter.getType().getAllInvolvedRawTypes().stream().filter(AllowedScopesArchitectureTest::isDomainObject)
                                .forEach(type -> events.add(violated(method, method.getFullName() + " takes the domain object " + type.getName() + " as the request body")));
                    }
                }
            }
        };
    }

    private static boolean isDomainObject(JavaClass type) {
        return type.isAssignableTo(DomainObject.class) || type.isAnnotatedWith("jakarta.persistence.Entity");
    }
}
