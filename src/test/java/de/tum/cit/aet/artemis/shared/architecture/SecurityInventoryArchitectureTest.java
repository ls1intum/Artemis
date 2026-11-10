package de.tum.cit.aet.artemis.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;

/**
 * Inventories that make a change of the attack surface visible in a pull request.
 * <p>
 * Each inventory is a reviewed list in {@code src/test/resources/security}. A test fails when the code differs from its list, in both directions: an entry that
 * is new has to be agreed to by a reviewer, and an entry that is gone has to be removed from the list, so the lists only ever describe what exists. An entry is
 * never added to make a failing test pass without asking what it allows.
 */
class SecurityInventoryArchitectureTest extends AbstractArchitectureTest {

    private static final String PUBLIC_ENDPOINTS = "security/public-endpoints.txt";

    private static final String ENTITY_REQUEST_BODIES = "security/entity-request-bodies.txt";

    private static final Set<Class<?>> MAPPING_ANNOTATIONS = Set.of(RequestMapping.class, GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class,
            DeleteMapping.class);

    /**
     * Every endpoint that can be called without signing in is listed. A new public endpoint changes who can reach the server, so it needs a reviewer to agree.
     */
    @Test
    void publicEndpointsAreReviewed() throws IOException {
        Set<String> actual = productionClasses.stream().flatMap(javaClass -> javaClass.getMethods().stream()).filter(method -> method.isAnnotatedWith(EnforceNothing.class))
                .map(SecurityInventoryArchitectureTest::describe).collect(Collectors.toCollection(TreeSet::new));
        assertMatchesInventory(PUBLIC_ENDPOINTS, actual, "public endpoints (annotated with @EnforceNothing)");
    }

    /**
     * A request body is a record that lists the accepted fields. An entity bound from a request lets the client set every field of it. Existing uses are listed, and
     * the list is to shrink: new code uses a DTO.
     */
    @Test
    void entitiesAreNotBoundFromRequestBodiesExceptTheListedOnes() throws IOException {
        List<JavaParameter> requestBodies = productionClasses.stream().flatMap(javaClass -> javaClass.getMethods().stream()).filter(SecurityInventoryArchitectureTest::isEndpoint)
                .flatMap(method -> method.getParameters().stream()).filter(SecurityInventoryArchitectureTest::isRequestBody).toList();
        // guards against a rule that finds nothing because the annotations were not recognized
        assertThat(requestBodies).as("the request bodies of the endpoints").hasSizeGreaterThan(100);
        Set<String> actual = requestBodies.stream().filter(parameter -> parameter.getRawType().isAssignableTo(DomainObject.class))
                .map(parameter -> describe((JavaMethod) parameter.getOwner()) + " binds " + parameter.getRawType().getSimpleName()).collect(Collectors.toCollection(TreeSet::new));
        assertMatchesInventory(ENTITY_REQUEST_BODIES, actual, "endpoints that bind an entity from the request body");
    }

    private static boolean isEndpoint(JavaMethod method) {
        return MAPPING_ANNOTATIONS.stream().anyMatch(annotation -> method.isAnnotatedWith(annotation.getName()));
    }

    private static boolean isRequestBody(JavaParameter parameter) {
        return parameter.isAnnotatedWith(RequestBody.class);
    }

    private static String describe(JavaMethod method) {
        JavaClass owner = method.getOwner();
        return owner.getName().substring(ARTEMIS_PACKAGE.length() + 1) + "#" + method.getName() + parameterTypes(method);
    }

    private static String parameterTypes(JavaMethod method) {
        return method.getRawParameterTypes().stream().map(JavaClass::getSimpleName).collect(Collectors.joining(",", "(", ")"));
    }

    private static void assertMatchesInventory(String resource, Set<String> actual, String description) throws IOException {
        Set<String> listed = readInventory(resource);
        Set<String> added = new TreeSet<>(actual);
        added.removeAll(listed);
        Set<String> removed = new TreeSet<>(listed);
        removed.removeAll(actual);
        assertThat(added).as("New %s. A reviewer has to agree before they are added to src/test/resources/%s:%n%s", description, resource, String.join("\n", added)).isEmpty();
        assertThat(removed).as("Entries of src/test/resources/%s that no longer exist (%s), remove them:%n%s", resource, description, String.join("\n", removed)).isEmpty();
    }

    private static Set<String> readInventory(String resource) throws IOException {
        try (InputStream stream = SecurityInventoryArchitectureTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as("the inventory %s exists", resource).isNotNull();
            List<String> lines = Arrays.asList(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
            return lines.stream().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
