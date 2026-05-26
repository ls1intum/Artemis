package de.tum.cit.aet.artemis.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;

/**
 * Verifies that every Hibernate constructor expression in a {@code @Query} annotation references
 * a class that actually exists on the classpath at the package it points to AND that the class
 * has at least one constructor with a matching argument count.
 *
 * <p>
 * Hibernate's HQL parser requires the fully-qualified name in {@code SELECT new <fqn>(...)} —
 * Java {@code import} declarations are not visible inside the JPQL string. When a class is renamed
 * or moved to a different package, or when the constructor signature drifts, the {@code @Query}
 * string is not updated by IDE refactorings, and the resulting
 * {@code SemanticException: Could not resolve class ...} or {@code Cannot apply constructor}
 * only surfaces at application-context-load time (during test runs).
 *
 * <p>
 * This rule scans every {@code @Query} on every method in production code, extracts each
 * {@code new <fqn>(...)} reference whose FQN is rooted at {@code de.tum.cit.aet.artemis}, and
 * asserts both:
 * <ol>
 * <li>the class resolves via {@link Class#forName(String)}; and</li>
 * <li>at least one declared constructor has a matching arg count (counted from top-level commas
 * in the argument block, with depth tracking so nested calls like {@code FOO(BAR(a, b), c)} are
 * counted as one outer arg of two characters not four).</li>
 * </ol>
 * Failures here turn a runtime regression into a fast PR-time check.
 */
class JpqlConstructorReferenceArchitectureTest extends AbstractArchitectureTest {

    /**
     * Matches {@code new <fqn>} where the FQN starts with {@code de.tum.cit.aet.artemis.}.
     * Captures the FQN (group 1). The greedy {@code [A-Za-z0-9_.$]+} is safe in JPQL because
     * a constructor expression takes the form {@code new <FQN>(args...)} — JPQL does not permit
     * field/method access on a class literal in this position, so there is nothing after the
     * class name except whitespace and the opening paren. The trailing context check
     * ({@code (?=\\s*\\()}) ensures we are actually in a constructor expression.
     */
    private static final Pattern JPQL_CONSTRUCTOR_HEAD = Pattern.compile("new\\s+(de\\.tum\\.cit\\.aet\\.artemis\\.[A-Za-z0-9_.$]+)(?=\\s*\\()");

    @Test
    void allJpqlConstructorReferencesResolveToExistingClasses() {
        List<String> violations = new ArrayList<>();

        productionClasses.stream().flatMap(javaClass -> javaClass.getMethods().stream()).forEach(method -> checkMethod(method, violations));

        assertThat(violations).as("""
                Found @Query JPQL `new <fqn>(...)` constructor expressions that reference classes which \
                no longer exist at the given package OR whose argument count does not match any declared \
                constructor. This is the exact class of bug that escapes a module-extraction refactor \
                because Hibernate only validates the constructor expression at application-context-load \
                time. Update the FQN inside the @Query string to match the class's current package, or \
                fix the argument list to match a real constructor.
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
        Matcher matcher = JPQL_CONSTRUCTOR_HEAD.matcher(jpql);
        while (matcher.find()) {
            String fqn = matcher.group(1);
            Class<?> clazz = loadClass(fqn);
            if (clazz == null) {
                violations.add("%s -> `new %s(...)` (class not found on classpath)".formatted(method.getFullName(), fqn));
                continue;
            }
            // Locate the matching open paren after the FQN and balance-count to find its closing paren.
            int openParen = jpql.indexOf('(', matcher.end());
            if (openParen < 0) {
                continue; // defensive — the regex's lookahead already required an open paren
            }
            int closeParen = findMatchingClose(jpql, openParen);
            if (closeParen < 0) {
                violations.add("%s -> `new %s(` (unmatched open paren in @Query string)".formatted(method.getFullName(), fqn));
                continue;
            }
            String argsBlock = jpql.substring(openParen + 1, closeParen);
            int argCount = countTopLevelArgs(argsBlock);
            if (!hasConstructorWithArity(clazz, argCount)) {
                violations.add("%s -> `new %s(%s args)` (no declared constructor with %d argument(s))".formatted(method.getFullName(), fqn, argCount, argCount));
            }
        }
    }

    private Class<?> loadClass(String fqn) {
        try {
            return Class.forName(fqn, false, getClass().getClassLoader());
        }
        catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns the index of the {@code )} that matches the {@code (} at {@code openIdx}, or -1 if
     * unbalanced. Tracks paren depth across nested calls; does not need to parse strings or
     * comments because JPQL constructor expressions are simple enough that quoted parens would
     * already break the surrounding query.
     */
    private static int findMatchingClose(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            }
            else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Counts top-level commas in an argument block (i.e. ignoring commas nested inside parens) and
     * returns {@code commas + 1}. An empty block returns 0. Conservative on whitespace: an
     * argument list that is entirely whitespace also returns 0.
     */
    private static int countTopLevelArgs(String argsBlock) {
        if (argsBlock.isBlank()) {
            return 0;
        }
        int depth = 0;
        int commas = 0;
        for (int i = 0; i < argsBlock.length(); i++) {
            char c = argsBlock.charAt(i);
            if (c == '(') {
                depth++;
            }
            else if (c == ')') {
                depth--;
            }
            else if (c == ',' && depth == 0) {
                commas++;
            }
        }
        return commas + 1;
    }

    private static boolean hasConstructorWithArity(Class<?> clazz, int arity) {
        return Arrays.stream(clazz.getDeclaredConstructors()).map(Constructor::getParameterCount).anyMatch(count -> count == arity);
    }
}
