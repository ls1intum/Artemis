package de.tum.cit.aet.artemis.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;

/**
 * Verifies that every Hibernate constructor expression in a {@code @Query} annotation references
 * a class that actually exists on the classpath at the package it points to.
 *
 * <p>
 * Hibernate's HQL parser requires the fully-qualified name in {@code SELECT new <fqn>(...)} —
 * Java {@code import} declarations are not visible inside the JPQL string. When a class is renamed
 * or moved to a different package, the {@code @Query} string is not updated by IDE refactorings,
 * and the resulting {@code SemanticException: Could not resolve class ...} only surfaces at
 * application-context-load time (during test runs).
 *
 * <p>
 * This rule scans every {@code @Query} on every method in production code, extracts each
 * {@code new <fqn>(} reference whose FQN is rooted at {@code de.tum.cit.aet.artemis}, and asserts
 * that the class resolves via {@link Class#forName(String)}. Failures here turn a runtime
 * regression into a fast PR-time check.
 */
class JpqlConstructorReferenceArchitectureTest extends AbstractArchitectureTest {

    /**
     * Matches {@code new <fqn>(} where the FQN starts with {@code de.tum.cit.aet.artemis.}.
     * Captures the FQN (group 1). Whitespace between {@code new} and the FQN is allowed.
     */
    private static final Pattern JPQL_CONSTRUCTOR = Pattern.compile("new\\s+(de\\.tum\\.cit\\.aet\\.artemis\\.[A-Za-z0-9_.$]+)\\s*\\(");

    @Test
    void allJpqlConstructorReferencesResolveToExistingClasses() {
        List<String> violations = new ArrayList<>();

        productionClasses.stream().flatMap(javaClass -> javaClass.getMethods().stream()).forEach(method -> checkMethod(method, violations));

        assertThat(violations).as("""
                Found @Query JPQL `new <fqn>(...)` constructor expressions that reference classes which no longer exist at the given package. \
                This is the exact class of bug that escapes a module-extraction refactor because Hibernate only validates the FQN at \
                application-context-load time. Update the FQN inside the @Query string to match the class's current package.
                Violations:
                %s""".formatted(String.join("\n  ", violations))).isEmpty();
    }

    private void checkMethod(JavaMethod method, List<String> violations) {
        method.getAnnotations().stream().filter(a -> a.getRawType().isAssignableTo(Query.class)).forEach(annotation -> checkQuery(method, annotation, violations));
    }

    private void checkQuery(JavaMethod method, JavaAnnotation<?> queryAnnotation, List<String> violations) {
        Object value = queryAnnotation.get("value").orElse(null);
        if (!(value instanceof String jpql)) {
            return;
        }
        Matcher matcher = JPQL_CONSTRUCTOR.matcher(jpql);
        while (matcher.find()) {
            String fqn = matcher.group(1);
            if (!classExists(fqn)) {
                violations.add("%s -> `new %s(...)` (class not found on classpath)".formatted(method.getFullName(), fqn));
            }
        }
    }

    private boolean classExists(String fqn) {
        try {
            Class.forName(fqn, false, getClass().getClassLoader());
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }
}
