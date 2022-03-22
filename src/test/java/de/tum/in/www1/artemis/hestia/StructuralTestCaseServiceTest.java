package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.hestia.structural.StructuralSolutionEntryGenerationException;
import de.tum.in.www1.artemis.service.hestia.structural.StructuralTestCaseService;
import de.tum.in.www1.artemis.util.HestiaUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

/**
 * Tests for the StructuralTestCaseService
 * Test if solution entries are generated as expected for structural tests
 */
public class StructuralTestCaseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final LocalRepository solutionRepo = new LocalRepository();

    private final LocalRepository testRepo = new LocalRepository();

    @Autowired
    private HestiaUtilTestService hestiaUtilTestService;

    @Autowired
    private StructuralTestCaseService structuralTestCaseService;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    public void initTestCase() throws Exception {
        Course course = database.addEmptyCourse();
        database.addUsers(0, 0, 0, 1);
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    private void addTestCaseToExercise(String name) {
        var testCase = new ProgrammingExerciseTestCase();
        testCase.setTestName(name);
        testCase.setExercise(exercise);
        testCase.setVisibility(Visibility.ALWAYS);
        testCase.setActive(true);
        testCase.setWeight(1D);
        testCase.setType(ProgrammingExerciseTestCaseType.STRUCTURAL);
        testCaseRepository.save(testCase);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForSimpleClass() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test {}", exercise, solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["public"],
                        "package": "test"
                    }
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testClass[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("package test;\npublic class Test {\n    \n}");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForComplexClass() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \nprivate abstract class Test extends Test2 implements TestI1, TestI2 {}", exercise,
                solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["private", "abstract"],
                        "package": "test",
                        "superclass": "Test2",
                        "interfaces": ["TestI1", "TestI2"]
                    }
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testClass[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("package test;\nprivate abstract class Test extends Test2 implements TestI1, TestI2 {\n    \n}");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForGenericClass() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test<T, E extends List<T>> {}", exercise, solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["public"],
                        "package": "test"
                    }
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testClass[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("package test;\npublic class Test<T, E extends List<T>> {\n    \n}");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForSimpleAttribute() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test {private String attributeName;}", exercise, solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["public"],
                        "package": "test"
                    },
                    "attributes" : [{
                        "name": "attributeName",
                        "modifiers": ["private"],
                        "type": "String"
                    }]
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testAttributes[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("private String attributeName;");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForComplexAttribute() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test {private static final List<Date> attributeName;}", exercise,
                solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["public"],
                        "package": "test"
                    },
                    "attributes" : [{
                        "name": "attributeName",
                        "modifiers": ["protected", "static", "final"],
                        "type": "List"
                    }]
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testAttributes[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("protected static final List<Date> attributeName;");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForSimpleConstructor() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test {public Test() {}}", exercise, solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["public"],
                        "package": "test"
                    },
                    "constructors": [{
                        "modifiers": ["public"],
                        "parameters": []
                    }]
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testConstructors[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("public Test() {\n    \n}");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForComplexConstructor() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test {protected Test(String s1, List<Date> dates) {}}", exercise,
                solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["public"],
                        "package": "test"
                    },
                    "constructors": [{
                        "modifiers": ["protected"],
                        "parameters": ["String","List"]
                    }]
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testConstructors[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("protected Test(String s1, List<Date> dates) {\n    \n}");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForSimpleMethod() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test {public void foo() {}}", exercise, solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["public"],
                        "package": "test"
                    },
                    "methods": [{
                        "name": "foo",
                        "modifiers": ["public"],
                        "parameters": [],
                        "returnType": "void"
                    }]
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testMethods[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("public void foo() {\n    \n}");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForComplexMethod() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test {protected static List<Date> foo(List<Object> list, String s) {}}",
                exercise, solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["public"],
                        "package": "test"
                    },
                    "methods": [{
                        "name": "foo",
                        "modifiers": ["protected", "static"],
                        "parameters": ["List", "String"],
                        "returnType": "List"
                    }]
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testMethods[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("protected static List<Date> foo(List<Object> list, String s) {\n    \n}");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForGenericMethod() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test {public <T, E extends List<T>> E foo(T[] arr) {}}", exercise,
                solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test.json", """
                [{
                    "class": {
                        "name": "Test",
                        "modifiers": ["public"],
                        "package": "test"
                    },
                    "methods": [{
                        "name": "foo",
                        "modifiers": ["public"],
                        "parameters": ["Object[]"],
                        "returnType": "List"
                    }]
                }]
                """, exercise, testRepo);
        addTestCaseToExercise("testMethods[Test]");

        var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
        assertThat(solutionEntries).hasSize(1);
        assertThat(solutionEntries.get(0).getFilePath()).isEqualTo("src/test/Test.java");
        assertThat(solutionEntries.get(0).getCode()).isEqualTo("public <T, E extends List<T>> E foo(T[] arr) {\n    \n}");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testForMissingTestJson() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("src/test/Test.java", "package test; \npublic class Test {}", exercise, solutionRepo);
        exercise = hestiaUtilTestService.setupTests("src/test/TestTest.java", "package test; \npublic class TestTest {}", exercise, testRepo);
        addTestCaseToExercise("testClass[Test]");

        assertThatExceptionOfType(StructuralSolutionEntryGenerationException.class).isThrownBy(() -> structuralTestCaseService.generateStructuralSolutionEntries(exercise));
    }
}
