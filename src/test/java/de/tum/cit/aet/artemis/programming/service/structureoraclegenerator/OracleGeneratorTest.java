package de.tum.cit.aet.artemis.programming.service.structureoraclegenerator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the structure oracle an instructor generates from a Java exercise.
 * <p>
 * The oracle is the difference between the solution and the template: everything the solution declares and the template
 * does not is what a student still has to write, and the structural tests are generated from exactly that. An element
 * missing from the oracle is never tested, and an element that should not be there tests students on code they were
 * given, so what ends up in the difference is the whole contract.
 */
class OracleGeneratorTest {

    @TempDir
    Path solutionProjectPath;

    @TempDir
    Path templateProjectPath;

    private void writeSolutionClass(String fileName, String source) throws IOException {
        FileUtils.write(solutionProjectPath.resolve(fileName).toFile(), source, StandardCharsets.UTF_8);
    }

    private void writeTemplateClass(String fileName, String source) throws IOException {
        FileUtils.write(templateProjectPath.resolve(fileName).toFile(), source, StandardCharsets.UTF_8);
    }

    private String generate() {
        return OracleGenerator.generateStructureOracleJSON(solutionProjectPath, templateProjectPath);
    }

    @Test
    void generateStructureOracleJSON_describesWhatTheSolutionAddsToTheTemplate() throws Exception {
        writeSolutionClass("BubbleSort.java", """
                package de.test;

                public class BubbleSort implements SortStrategy {

                    private int comparisons;

                    public void performSort(int[] input) {
                    }
                }
                """);
        writeTemplateClass("BubbleSort.java", """
                package de.test;

                public class BubbleSort implements SortStrategy {
                }
                """);

        String oracle = generate();

        assertThat(oracle).as("the class the student has to complete is part of the oracle").contains("\"name\" : \"BubbleSort\"").contains("\"package\" : \"de.test\"");
        assertThat(oracle).as("the method the template does not declare is what the student has to write").contains("\"methods\"").contains("performSort");
        assertThat(oracle).as("the attribute the template does not declare is required as well").contains("\"attributes\"").contains("comparisons");
    }

    @Test
    void generateStructureOracleJSON_leavesOutAClassTheTemplateAlreadyProvidesInFull() throws Exception {
        String identicalSource = """
                package de.test;

                public class Context {

                    public void alreadyGiven() {
                    }
                }
                """;
        writeSolutionClass("Context.java", identicalSource);
        writeTemplateClass("Context.java", identicalSource);

        String oracle = generate();

        // Testing students on code they were handed produces failures they cannot act on.
        assertThat(oracle).as("a class the student is given in full is not part of the oracle").doesNotContain("Context");
        assertThat(oracle.strip()).isEqualTo("[ ]");
    }

    @Test
    void generateStructureOracleJSON_includesAClassTheTemplateDoesNotHaveAtAll() throws Exception {
        writeSolutionClass("MergeSort.java", """
                package de.test;

                public class MergeSort implements SortStrategy {

                    public void performSort(int[] input) {
                    }
                }
                """);

        String oracle = generate();

        assertThat(oracle).as("a class the student has to create from nothing is part of the oracle").contains("MergeSort").contains("performSort");
    }

    @Test
    void generateStructureOracleJSON_describesTheConstructorsAndTheEnumValuesTheStudentHasToAdd() throws Exception {
        writeSolutionClass("Policy.java", """
                package de.test;

                public class Policy {

                    public Policy(Context context) {
                    }
                }
                """);
        writeTemplateClass("Policy.java", """
                package de.test;

                public class Policy {
                }
                """);
        writeSolutionClass("SortOrder.java", """
                package de.test;

                public enum SortOrder {
                    ASCENDING, DESCENDING
                }
                """);
        writeTemplateClass("SortOrder.java", """
                package de.test;

                public enum SortOrder {
                }
                """);

        String oracle = generate();

        assertThat(oracle).as("a constructor the template does not declare is part of the oracle").contains("\"constructors\"");
        assertThat(oracle).as("the enum values the student has to add are part of the oracle").contains("\"enumValues\"").contains("ASCENDING").contains("DESCENDING");
    }

    @Test
    void generateStructureOracleJSON_forAProjectWithoutAnyJavaFile_producesAnEmptyOracle() {
        // An exercise whose solution repository is empty has nothing to test structurally, which has to come out as an empty oracle rather than as a failure.
        assertThat(generate().strip()).isEqualTo("[ ]");
    }

    @Test
    void generateStructureOracleJSON_readsTheSourcesOfNestedPackages() throws Exception {
        writeSolutionClass("de/test/sorting/QuickSort.java", """
                package de.test.sorting;

                public class QuickSort {

                    public void performSort(int[] input) {
                    }
                }
                """);

        String oracle = generate();

        assertThat(oracle).as("a class in a nested package directory is found as well").contains("QuickSort").contains("\"package\" : \"de.test.sorting\"");
    }
}
