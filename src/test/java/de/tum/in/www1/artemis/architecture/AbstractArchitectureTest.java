package de.tum.in.www1.artemis.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.equalTo;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.base.DescribedPredicate.or;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;

@Tag("ArchitectureTest")
public abstract class AbstractArchitectureTest {

    protected static final String ARTEMIS_PACKAGE = "de.tum.in.www1.artemis";

    protected static JavaClasses testClasses;

    protected static JavaClasses allClasses;

    protected static JavaClasses allClassesWithHazelcast;

    protected static JavaClasses productionClasses;

    @BeforeAll
    static void loadClasses() {
        if (allClasses == null) {
            testClasses = new ClassFileImporter().withImportOption(new ImportOption.OnlyIncludeTests()).importPackages(ARTEMIS_PACKAGE);
            productionClasses = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages(ARTEMIS_PACKAGE);
            allClasses = new ClassFileImporter().importPackages(ARTEMIS_PACKAGE);
            // Also include hazelcast to find usages of hazelcast methods. (see testNoHazelcastUsageInConstructors)
            allClassesWithHazelcast = new ClassFileImporter().importPackages(ARTEMIS_PACKAGE, "com.hazelcast.core");
        }
        ensureClassSetsNonEmpty();
        ensureAllClassesFound();
    }

    private static void ensureClassSetsNonEmpty() {
        assertThat(testClasses).isNotEmpty();
        assertThat(productionClasses).isNotEmpty();
        assertThat(allClasses).isNotEmpty();
    }

    private static void ensureAllClassesFound() {
        assertThat(testClasses.size() + productionClasses.size()).isEqualTo(allClasses.size());
    }

    /**
     * Excludes the classes with the given simple names from the ArchUnit rule checks.
     * <p>
     * IMPORTANT: This method should only be used for classes that are not public. {@link #classesExcept(JavaClasses, Class...)} is recommended for public classes.
     *
     * @param classes    The classes to be filtered.
     * @param exceptions The simple names of the classes to be excluded.
     * @return A JavaClasses object excluding the specified classes.
     */
    protected JavaClasses classesExcept(JavaClasses classes, String... exceptions) {
        var predicates = Arrays.stream(exceptions).map(JavaClass.Predicates::simpleName).toList();
        return classes.that(not(or(predicates)));
    }

    /**
     * Excludes the given classes from the ArchUnit rule checks.
     *
     * @param classes    The classes to be filtered.
     * @param exceptions The classes to be excluded.
     * @return A JavaClasses object excluding the specified classes.
     */
    protected JavaClasses classesExcept(JavaClasses classes, Class<?>... exceptions) {
        return classes.that(not(belongToAnyOf(exceptions)));
    }

    // Custom Predicates for JavaAnnotations since ArchUnit only defines them for classes

    protected static DescribedPredicate<? super JavaAnnotation<?>> simpleNameAnnotation(String name) {
        return equalTo(name).as("Annotation with simple name " + name).onResultOf(annotation -> annotation.getRawType().getSimpleName());
    }

    protected DescribedPredicate<? super JavaAnnotation<?>> resideInPackageAnnotation(String packageName) {
        return equalTo(packageName).as("Annotation in package " + packageName).onResultOf(annotation -> annotation.getRawType().getPackageName());
    }

    protected DescribedPredicate<? super JavaCodeUnit> declaredClassSimpleName(String name) {
        return equalTo(name).as("Declared in class with simple name " + name).onResultOf(unit -> unit.getOwner().getSimpleName());
    }

    protected ArchCondition<JavaMethod> haveAllParametersAnnotatedWithUnless(DescribedPredicate<? super JavaAnnotation<?>> annotationPredicate,
            DescribedPredicate<JavaClass> exception) {
        return new ArchCondition<>("have all parameters annotated with " + annotationPredicate.getDescription()) {

            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                boolean satisfied = item.getParameters().stream()
                        // Ignore annotations of the Pageable parameter
                        .filter(javaParameter -> !exception.test(javaParameter.getRawType())).map(JavaParameter::getAnnotations)
                        // Else, one of the annotations should match the given predicate
                        // This allows parameters with multiple annotations (e.g. @NonNull @Param)
                        .allMatch(annotations -> annotations.stream().anyMatch(annotationPredicate));
                if (!satisfied) {
                    events.add(violated(item, String.format("Method %s has parameter violating %s", item.getFullName(), annotationPredicate.getDescription())));
                }
            }
        };
    }

    protected ArchCondition<JavaMethod> notHaveAnyParameterAnnotatedWith(DescribedPredicate<? super JavaAnnotation<?>> annotationPredicate) {
        return new ArchCondition<>("not have parameters annotated with " + annotationPredicate.getDescription()) {

            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                boolean satisfied = item.getParameterAnnotations().stream().flatMap(Collection::stream).noneMatch(annotationPredicate);
                if (!satisfied) {
                    events.add(violated(item, String.format("Method %s has parameter violating %s", item.getFullName(), annotationPredicate.getDescription())));
                }
            }
        };
    }

    /**
     * Checks if the given method has a parameter with the given name.
     *
     * @param method    The method to check.
     * @param paramName The name of the parameter to look for.
     * @return True if the method has a parameter with the given name, false otherwise.
     */
    protected boolean hasParameterWithName(JavaMethod method, String paramName) {
        try {
            var owner = method.getOwner();
            var javaClass = Class.forName(owner.getFullName());
            var javaMethod = javaClass.getMethod(method.getName(), method.getRawParameterTypes().stream().map(this::getClassForName).toArray(Class[]::new));
            return Arrays.stream(javaMethod.getParameters()).anyMatch(parameter -> parameter.getName().equals(paramName));
        }
        catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks if the given method has no parameter with the given name.
     *
     * @param method    The method to check.
     * @param paramName The name of the parameter to look for.
     * @return True if the method has no parameter with the given name, false otherwise.
     */
    protected boolean hasNoParameterWithName(JavaMethod method, String paramName) {
        return !hasParameterWithName(method, paramName);
    }

    /**
     * Returns the class for the given JavaClass.
     *
     * @param paramClass The JavaClass to get the class for.
     * @return The class for the given JavaClass.
     */
    protected Class<?> getClassForName(JavaClass paramClass) {
        try {
            return ClassUtils.getClass(paramClass.getName());
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the annotation of the given class or any of its superclasses.
     *
     * @param clazz      The annotation class to look for.
     * @param javaMethod The JavaMethod to get the annotation from.
     * @param <T>        The type of the annotation.
     * @return The annotation of the given class or any of its superclasses.
     */
    protected <T extends Annotation> T getAnnotation(Class<T> clazz, JavaMethod javaMethod) {
        final var method = javaMethod.reflect();
        T annotation = method.getAnnotation(clazz);
        if (annotation != null) {
            return annotation;
        }
        for (Annotation a : method.getDeclaredAnnotations()) {
            annotation = a.annotationType().getAnnotation(clazz);
            if (annotation != null) {
                return annotation;
            }
        }

        annotation = method.getDeclaringClass().getAnnotation(clazz);
        if (annotation != null) {
            return annotation;
        }
        for (Annotation a : method.getDeclaringClass().getDeclaredAnnotations()) {
            annotation = a.annotationType().getAnnotation(clazz);
            if (annotation != null) {
                return annotation;
            }
        }

        return null;
    }
}
