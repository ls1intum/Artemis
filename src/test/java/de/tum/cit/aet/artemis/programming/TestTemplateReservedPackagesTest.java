package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/**
 * The student code is compiled into the classpath of the tests. Every package the tests, Ares or their libraries live in has to be rejected for student
 * classes, otherwise a submission can shadow them, e.g. with an assertion class that never fails, and forge its own grading.
 * The check exists once per build tool template, so this test makes sure none of them drops one of the packages.
 */
class TestTemplateReservedPackagesTest {

    private static final List<String> PACKAGES_STUDENTS_MUST_NOT_DEFINE = List.of("java", "javax", "jdk", "sun", "com/sun", "de/tum/cit/ase/ares", "net/bytebuddy", "org/aspectj",
            "com/ibm/wala", "com/tngtech/archunit", "org/junit", "junit", "org/opentest4j", "org/assertj", "org/hamcrest", "org/mockito", "org/apache", "org/slf4j",
            "ch/qos/logback", "com/fasterxml", "com/google", "org/json", "org/yaml");

    @ParameterizedTest
    @ValueSource(strings = { "templates/java/test/maven/projectTemplate/pom.xml", "templates/java/maven_maven/test/projectTemplate/pom.xml",
            "templates/java/test/gradle/projectTemplate/gradle/AresReservedPackages.gradle", "templates/kotlin/test/maven/projectTemplate/pom.xml" })
    void testTestTemplateRejectsStudentClassesInTrustedPackages(String template) throws IOException {
        String content = new ClassPathResource(template).getContentAsString(StandardCharsets.UTF_8);

        // The pattern has to start right after the quote, otherwise "sun/**" would be satisfied by "com/sun/**".
        assertThat(PACKAGES_STUDENTS_MUST_NOT_DEFINE).allSatisfy(reservedPackage -> assertThat(content).satisfiesAnyOf(c -> assertThat(c).contains("\"" + reservedPackage + "/**"),
                c -> assertThat(c).contains("'" + reservedPackage + "/**")));
    }
}
