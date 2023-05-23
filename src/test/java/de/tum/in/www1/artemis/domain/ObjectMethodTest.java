package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.domain.ObjectMethodTestExclusions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.util.classpath.ClassNode;
import de.tum.in.www1.artemis.util.classpath.ClassPathNode;
import de.tum.in.www1.artemis.util.classpath.ClassPathUtil;
import io.github.classgraph.ClassInfo;

/**
 * Tests that the methods from {@link Object} are properly overridden.
 */
class ObjectMethodTest {

    private static final Logger log = LoggerFactory.getLogger(ObjectMethodTest.class);

    private static final String GENERATE_TESTS = "Generate tests";

    private static final String DOMAIN_PACKAGE_NAME = "de.tum.in.www1.artemis.domain";

    private static final Map<Class<?>, List<?>> ID_TEST_VALUES = Map.of(Long.class, List.of(1L, 42L), String.class, List.of("A", "B"));

    /**
     * Generates test in form of a hierarchical structure, see also JUnit 5 dynamic tests
     */
    @TestFactory
    DynamicNode testDomainClasses() {
        var allDomainClasses = ClassPathUtil.findAllClassesIn(DOMAIN_PACKAGE_NAME, ObjectMethodTest::classPathElementFilter);
        var domainClassTests = generateTestContainerForClasses(allDomainClasses);
        return domainClassTests.orElseGet(() -> dynamicTest(GENERATE_TESTS, () -> fail("No testable domain classes found")));
    }

    /**
     * Maps the class path structure to the dynamic test structure.
     *
     * @return the test structure or an empty optional, if no tests could be generated for the whole ClassPathNode
     */
    Optional<DynamicNode> generateTestContainerForClasses(ClassPathNode classPathStructure) {
        return classPathStructure.mapTree(this::generateTestsForClass, (packageNode, dynamicNodes) -> {
            var tests = dynamicNodes.filter(Optional::isPresent).map(Optional::get).toList();
            if (tests.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(dynamicContainer("[" + packageNode.getSegmentName() + "]", tests));
        });
    }

    /**
     * Maps a single domain class node to its dynamic test structure. This basically just generates tests based on the properties of the class.
     *
     * @return the test structure or an empty optional, if no tests could be generated for this ClassNode
     */
    Optional<DynamicNode> generateTestsForClass(ClassNode classNode) {
        Class<?> domainClass = classNode.getContainedClass();
        ClassInfo info = classNode.getClassInfo();
        // Ignore interfaces, abstract classes, anonymous classes, annotations and generated classes
        if (info.isInterfaceOrAnnotation() || info.isAnonymousInnerClass() || info.isAbstract() || info.getName().endsWith("_")) {
            return Optional.empty();
        }
        // Ignore classes that are excluded from everything
        if (isClassExcludedFrom(domainClass, ALL_TESTS)) {
            return Optional.empty();
        }

        // Add tests
        List<DynamicNode> tests = new ArrayList<>();
        if (info.isEnum()) {
            generateTestsForEnum(tests, domainClass);
        }
        else {
            generateTestsForStandardClasses(tests, domainClass, info);
        }

        // Only return a test container if there are any tests that can be run
        if (!tests.isEmpty()) {
            return Optional.of(dynamicContainer(classNode.getSegmentName(), tests));
        }
        return Optional.empty();
    }

    /**
     * Generates tests for {@link Enum}s in the Artemis domain package
     */
    private static void generateTestsForEnum(List<DynamicNode> tests, Class<?> domainClass) {
        // test toString and name()
        if (isClassNotExcludedFrom(domainClass, ENUM_TOSTRING_NAME_EQUALITY)) {
            tests.add(dynamicContainer("toString() and name() are equal", Stream.of(domainClass.getEnumConstants()).map(constant -> {
                var name = ((Enum<?>) constant).name();
                return dynamicTest(name, () -> assertThat(constant).hasToString(name));
            })));
        }
        /*
         * equals() and hashCode() cannot be changed for Enum and don't need to be tested
         */
    }

    /**
     * Generates tests for regular classes in the Artemis domain package
     */
    private static void generateTestsForStandardClasses(List<DynamicNode> tests, Class<?> domainClass, ClassInfo info) {
        // Tests that don't require instances
        // >>> None at the moment <<<

        // Tests that depend on creating instances
        if (isClassExcludedFrom(domainClass, INSTANCE_TESTS)) {
            return;
        }
        // try to find a no-args constructor
        var noArgsConstructors = info.getConstructorInfo().filter(f -> f.getParameterInfo().length == 0);
        if (noArgsConstructors.size() == 1) {
            try {
                var noArgsConstructor = domainClass.getDeclaredConstructor();

                // check that none of toString(), equals(null) and hashCode() throws any exceptions
                generateObjectMethodTests(tests, noArgsConstructor);

                // check id related object methods
                if (info.hasMethod("getId") && info.hasMethod("setId")) {
                    generateIdRelatedTests(tests, domainClass, noArgsConstructor);
                }
            }
            catch (ReflectiveOperationException e) {
                tests.add(dynamicTest(GENERATE_TESTS, () -> fail("Relective operation failed", e)));
            }
        }
        else {
            log.warn("{} does not have a no-args constructor", domainClass);
        }
    }

    private static void generateObjectMethodTests(List<DynamicNode> tests, Constructor<?> noArgsConstructor) {
        tests.add(dynamicTest("toString() does not throw exceptions", () -> {
            Object instance = noArgsConstructor.newInstance();
            assertThatNoException().isThrownBy(instance::toString);
        }));
        tests.add(dynamicTest("equals(null) does not throw exceptions", () -> {
            Object instance = noArgsConstructor.newInstance();
            assertThatNoException().isThrownBy(() -> instance.equals(null));
        }));
        tests.add(dynamicTest("hashCode() does not throw exceptions", () -> {
            Object instance = noArgsConstructor.newInstance();
            assertThatNoException().isThrownBy(instance::hashCode);
        }));
    }

    private static void generateIdRelatedTests(List<DynamicNode> tests, Class<?> domainClass, Constructor<?> noArgsConstructor) throws NoSuchMethodException {
        if (isClassExcludedFrom(domainClass, ID_RELATED_TESTS)) {
            return;
        }
        var getId = domainClass.getMethod("getId");
        var idType = getId.getReturnType();
        var setId = domainClass.getMethod("setId", idType);
        var testValues = ID_TEST_VALUES.get(idType);

        if (testValues == null || testValues.size() != 2) {
            tests.add(dynamicTest(GENERATE_TESTS, () -> fail("No id test values found for " + idType)));
        }
        else {
            var idA = testValues.get(0);
            var idB = testValues.get(1);

            if (isClassNotExcludedFrom(domainClass, ID_GET_AND_SET)) {
                // getId and setId
                tests.add(dynamicTest("getId() and setId(" + idType.getSimpleName() + ") match each other", () -> {
                    Object instance = noArgsConstructor.newInstance();
                    assertThat(getId.invoke(instance)).isNull();
                    setId.invoke(instance, idA);
                    assertThat(getId.invoke(instance)).isEqualTo(idA);
                }));
            }
            if (isClassNotExcludedFrom(domainClass, ID_HASHCODE)) {
                // two instances with the same id
                tests.add(dynamicTest("hashCode() for two instances with the same id is equal", () -> {
                    Object instanceA = noArgsConstructor.newInstance();
                    Object instanceB = noArgsConstructor.newInstance();
                    setId.invoke(instanceA, idA);
                    setId.invoke(instanceB, idA);
                    assertThat(instanceA).hasSameHashCodeAs(instanceB);
                }));
            }
            if (isClassNotExcludedFrom(domainClass, ID_EQUALS)) {
                // two instances without id
                tests.add(dynamicTest("equals(Object) for two instances without id returns false", () -> {
                    Object instanceA = noArgsConstructor.newInstance();
                    Object instanceB = noArgsConstructor.newInstance();
                    assertThat(instanceA).isNotEqualTo(instanceB);
                }));
                // two instances with the same id
                tests.add(dynamicTest("equals(Object) for two instances with the same id returns true", () -> {
                    Object instanceA = noArgsConstructor.newInstance();
                    Object instanceB = noArgsConstructor.newInstance();
                    setId.invoke(instanceA, idA);
                    setId.invoke(instanceB, idA);
                    assertThat(instanceA).isEqualTo(instanceB);
                }));
                // two instances with different id
                tests.add(dynamicTest("equals(Object) for two instances with different id returns false", () -> {
                    Object instanceA = noArgsConstructor.newInstance();
                    Object instanceB = noArgsConstructor.newInstance();
                    setId.invoke(instanceA, idA);
                    setId.invoke(instanceB, idB);
                    assertThat(instanceA).isNotEqualTo(instanceB);
                }));
            }
        }

    }

    private static boolean classPathElementFilter(String classPathElement) {
        return classPathElement.contains("bin/main") || classPathElement.contains("build/classes/java/main");
    }
}
