package de.tum.cit.aet.artemis.shared.architecture;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;

/**
 * Reports production methods that only the tests call.
 * <p>
 * The class-level gate in {@code supporting_scripts/check_dead_code.py} cannot see this: a class stays alive as long as
 * anything names it, so a method inside it that lost its last production caller is invisible. A method kept alive only
 * by its own test is dead weight — the test asserts behaviour nothing asks for any more.
 * <p>
 * This is deliberately <b>advisory</b>: it logs a report and never fails. Method-level reachability in a Spring/JPA/
 * Jackson application has a false-positive floor that class-level reachability does not, because frameworks call
 * methods reflectively with no bytecode reference: Hibernate reads entity properties, Jackson reads DTO accessors, JPQL
 * names properties as strings, AspectJ weaves advice. The filters below remove the categories we can recognise; what is
 * left still needs a human to look at each entry before deleting anything. Once the report has been triaged and its
 * false-positive rate is understood, it can be turned into a gate with a recorded baseline.
 * <p>
 * Simple getters and setters are excluded outright. They dominated an earlier measurement (321 of 402 findings) and are
 * exactly the shape that reflective access reaches without a call site, so they carry no signal here.
 */
class UnusedMethodArchitectureTest extends AbstractArchitectureTest {

    private static final Logger log = LoggerFactory.getLogger(UnusedMethodArchitectureTest.class);

    /**
     * Annotations that make a method an entry point: a framework invokes it, so no call site exists in our code.
     */
    private static final Set<String> ENTRY_POINT_ANNOTATIONS = Set.of("org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping", "org.springframework.web.bind.annotation.PostMapping", "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping", "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.ExceptionHandler", "org.springframework.web.bind.annotation.ModelAttribute",
            "org.springframework.web.bind.annotation.InitBinder", "org.springframework.scheduling.annotation.Scheduled", "org.springframework.scheduling.annotation.Async",
            "org.springframework.context.event.EventListener", "org.springframework.transaction.event.TransactionalEventListener", "org.springframework.context.annotation.Bean",
            "org.springframework.messaging.handler.annotation.MessageMapping", "org.springframework.messaging.simp.annotation.SubscribeMapping",
            "org.springframework.jms.annotation.JmsListener", "org.springframework.boot.actuate.endpoint.annotation.ReadOperation",
            "org.springframework.boot.actuate.endpoint.annotation.WriteOperation", "org.springframework.boot.actuate.endpoint.annotation.DeleteOperation",
            "org.aspectj.lang.annotation.Around", "org.aspectj.lang.annotation.Before", "org.aspectj.lang.annotation.After", "org.aspectj.lang.annotation.AfterReturning",
            "org.aspectj.lang.annotation.AfterThrowing", "org.aspectj.lang.annotation.Pointcut", "jakarta.annotation.PostConstruct", "jakarta.annotation.PreDestroy",
            "jakarta.persistence.PrePersist", "jakarta.persistence.PostPersist", "jakarta.persistence.PreUpdate", "jakarta.persistence.PostUpdate", "jakarta.persistence.PreRemove",
            "jakarta.persistence.PostRemove", "jakarta.persistence.PostLoad", "com.fasterxml.jackson.annotation.JsonProperty", "com.fasterxml.jackson.annotation.JsonValue",
            "com.fasterxml.jackson.annotation.JsonCreator", "com.fasterxml.jackson.annotation.JsonGetter", "com.fasterxml.jackson.annotation.JsonSetter",
            "com.fasterxml.jackson.annotation.JsonAnySetter", "com.fasterxml.jackson.annotation.JsonAnyGetter");

    private static final Set<String> UNIVERSAL_METHODS = Set.of("equals", "hashCode", "toString", "main", "values", "valueOf", "ordinal", "name", "clone", "finalize",
            "readResolve", "writeReplace");

    @Test
    void reportProductionMethodsOnlyCalledByTests() {
        Set<String> testClassNames = new HashSet<>();
        testClasses.forEach(javaClass -> testClassNames.add(javaClass.getFullName()));

        List<String> testOnly = new ArrayList<>();
        int unreferenced = 0;
        int skipped = 0;

        for (JavaClass javaClass : allClasses) {
            if (testClassNames.contains(javaClass.getFullName()) || javaClass.isRecord() || javaClass.isAnnotation()) {
                continue;
            }
            for (JavaMethod method : javaClass.getMethods()) {
                if (shouldSkip(javaClass, method)) {
                    skipped++;
                    continue;
                }
                boolean calledByProduction = false;
                boolean calledByTest = false;
                for (JavaAccess<?> access : accessesTo(method)) {
                    if (testClassNames.contains(access.getOriginOwner().getFullName())) {
                        calledByTest = true;
                    }
                    else {
                        calledByProduction = true;
                    }
                }
                if (calledByProduction) {
                    continue;
                }
                if (calledByTest) {
                    testOnly.add(signatureOf(javaClass, method));
                }
                else {
                    unreferenced++;
                }
            }
        }

        testOnly.sort(String::compareTo);
        StringBuilder report = new StringBuilder("\n\nProduction methods that only the tests call: ").append(testOnly.size()).append('\n');
        report.append("(also seen: ").append(unreferenced).append(" methods with no caller at all, and ").append(skipped)
                .append(" skipped as entry points, overrides, accessors or universal methods)\n\n");
        testOnly.forEach(entry -> report.append("    ").append(entry).append('\n'));
        report.append("\nEach entry is a candidate, not a verdict: a framework may still reach it reflectively. Check the\n")
                .append("call path before deleting, and if the method really is only exercised by its own test, remove both.\n");
        log.info(report.toString());
    }

    /**
     * Renders a method as {@code package.Class#name(ParamType, ...)}. The parameters matter: an overload that
     * production still calls and one that only a test calls are otherwise indistinguishable in the report.
     *
     * @param javaClass the declaring class
     * @param method    the method to render
     * @return the readable signature
     */
    private String signatureOf(JavaClass javaClass, JavaMethod method) {
        String parameters = method.getRawParameterTypes().stream().map(JavaClass::getSimpleName).collect(java.util.stream.Collectors.joining(", "));
        return javaClass.getFullName().replace("de.tum.cit.aet.artemis.", "") + "#" + method.getName() + "(" + parameters + ")";
    }

    /**
     * Collects every recorded access to the method: ordinary calls and method references alike.
     *
     * @param method the method to inspect
     * @return all accesses pointing at that method
     */
    private Set<JavaAccess<?>> accessesTo(JavaMethod method) {
        Set<JavaAccess<?>> accesses = new LinkedHashSet<>(method.getAccessesToSelf());
        accesses.addAll(method.getReferencesToSelf());
        return accesses;
    }

    private boolean shouldSkip(JavaClass javaClass, JavaMethod method) {
        String name = method.getName();
        if (UNIVERSAL_METHODS.contains(name) || name.contains("$") || method.getModifiers().contains(JavaModifier.SYNTHETIC)) {
            return true;
        }
        if (isSimpleAccessor(method) || isFieldShapedAccessor(javaClass, method)) {
            return true;
        }
        if (method.getAnnotations().stream().anyMatch(annotation -> ENTRY_POINT_ANNOTATIONS.contains(annotation.getRawType().getFullName()))) {
            return true;
        }
        // A Spring Data repository method is implemented by the framework and called through the interface; an unused
        // one is real dead code, but it is reported by its own declaring interface rather than by every implementation.
        return overridesSomething(javaClass, method);
    }

    /**
     * A simple getter or setter, by shape: {@code getX()} / {@code isX()} / {@code hasX()} with no parameters, or
     * {@code setX(value)} with exactly one. Excluded because reflective access reaches exactly this shape without
     * leaving a call site, so the absence of a caller says nothing.
     *
     * @param method the method to classify
     * @return whether it looks like a simple accessor
     */
    private boolean isSimpleAccessor(JavaMethod method) {
        String name = method.getName();
        int parameterCount = method.getRawParameterTypes().size();
        boolean getterName = (name.startsWith("get") || name.startsWith("is") || name.startsWith("has")) && name.length() > 3
                && Character.isUpperCase(name.charAt(name.startsWith("is") ? 2 : 3));
        if (getterName && parameterCount == 0) {
            return true;
        }
        return name.startsWith("set") && name.length() > 3 && Character.isUpperCase(name.charAt(3)) && parameterCount == 1;
    }

    /**
     * An accessor that does not use the {@code get}/{@code set} prefix: {@code complaintText()} reading the field of
     * that name, or the fluent {@code complaintText(value)} returning {@code this}. Entities and DTOs here are full of
     * both, Jackson and Hibernate reach them reflectively, and they carry the same absence of signal as a plain getter.
     *
     * @param javaClass the declaring class
     * @param method    the method to classify
     * @return whether the method is named after a field and shaped like an accessor for it
     */
    private boolean isFieldShapedAccessor(JavaClass javaClass, JavaMethod method) {
        boolean namesAField = javaClass.getFields().stream().anyMatch(field -> field.getName().equals(method.getName()));
        if (!namesAField) {
            return false;
        }
        int parameterCount = method.getRawParameterTypes().size();
        if (parameterCount == 0) {
            return true;
        }
        String returnType = method.getRawReturnType().getFullName();
        return parameterCount == 1 && (returnType.equals(javaClass.getFullName()) || "void".equals(returnType));
    }

    /**
     * Whether the method overrides or implements anything. Such a method is invoked through the supertype, and ArchUnit
     * records that call against the supertype's declaration, so the override itself looks uncalled. Reflection is used
     * rather than ArchUnit's class graph because supertypes outside the Artemis packages are imported as stubs that
     * carry no methods.
     *
     * @param javaClass the declaring class
     * @param method    the method to check
     * @return whether some supertype declares the same signature
     */
    private boolean overridesSomething(JavaClass javaClass, JavaMethod method) {
        try {
            Method reflected = method.reflect();
            if (Modifier.isStatic(reflected.getModifiers()) || Modifier.isPrivate(reflected.getModifiers())) {
                return false;
            }
            Set<Class<?>> supertypes = new LinkedHashSet<>();
            collectSupertypes(javaClass.reflect(), supertypes);
            for (Class<?> supertype : supertypes) {
                try {
                    supertype.getDeclaredMethod(reflected.getName(), reflected.getParameterTypes());
                    return true;
                }
                catch (NoSuchMethodException ignored) {
                    // this supertype does not declare it; keep looking
                }
            }
            return false;
        }
        catch (Throwable failedToResolve) {
            // A class we cannot load or reflect on is not worth guessing about; stay silent rather than report noise.
            return true;
        }
    }

    private void collectSupertypes(Class<?> type, Set<Class<?>> collected) {
        Class<?> superclass = type.getSuperclass();
        if (superclass != null && superclass != Object.class && collected.add(superclass)) {
            collectSupertypes(superclass, collected);
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (collected.add(interfaceType)) {
                collectSupertypes(interfaceType, collected);
            }
        }
    }
}
