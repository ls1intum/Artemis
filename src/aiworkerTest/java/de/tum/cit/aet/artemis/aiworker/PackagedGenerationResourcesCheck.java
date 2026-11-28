package de.tum.cit.aet.artemis.aiworker;

import java.io.IOException;
import java.nio.file.Path;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationResources;

/** Runs inside Spring Boot's nested archive classloader, with no checkout on its classpath. */
public final class PackagedGenerationResourcesCheck {

    private PackagedGenerationResourcesCheck() {
    }

    public static void main(String[] args) throws IOException {
        var resources = new GenerationResources();
        for (var path : new String[] { "prompts/hyperion/critic/specification_review_system.st", "templates/java/gradle_gradle/readme", "templates/phases/java/plain_gradle.yaml",
                "templates/phases/java/plain_maven.yaml", "templates/hyperion/structural/GenericTypeTest.java" }) {
            if (resources.getResource(Path.of(path)).getContentAsByteArray().length == 0) {
                throw new IllegalStateException("Empty generation resource " + path);
            }
        }
        requireFixture(resources, "templates/hyperion/readiness/java", "ScoreCalculatorTest.java");
        requireFixture(resources, "templates/hyperion/style", "solution.md");
        requireFixture(resources, "templates/java/exercise", "BubbleSort.java");
        requireFixture(resources, "templates/java/test/testFiles/structural", "test.json");
    }

    private static void requireFixture(GenerationResources resources, String directory, String filename) throws IOException {
        for (var fixture : resources.getFileResources(Path.of(directory))) {
            if (filename.equals(fixture.getFilename()) && fixture.getContentAsByteArray().length > 0) {
                return;
            }
        }
        throw new IllegalStateException("The worker could not discover " + filename + " in " + directory);
    }
}
