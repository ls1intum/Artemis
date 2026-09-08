package de.tum.cit.aet.artemis.hyperionworker.generation;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Reads packaged authoring prompts and canonical exercise fixtures, never caller-supplied URLs. */
public class GenerationResources {

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * Adapts canonical teaching fixtures to Gradle output paths and stable JUnit report names.
     *
     * @param source canonical fixture text
     * @return the Gradle teaching fixture
     */
    public static String javaGradleFixture(String source) {
        return source.replace("@WhitelistPath(\"target\")", "@WhitelistPath(\"build\")")
                .replace("@BlacklistPath(\"target/test-classes\")", "@BlacklistPath(\"build/classes/java/test\")")
                .replace("@Public\n", "@org.junit.jupiter.api.DisplayNameGeneration(org.junit.jupiter.api.DisplayNameGenerator.Simple.class)\n@Public\n");
    }

    /**
     * Aligns test labels in the packaged Gradle teaching statement with the fixture's Simple display-name generator.
     * Never apply this to instructor or generated statements, whose bindings must match their reports without rewriting.
     *
     * @param source canonical teaching statement
     * @return statement with matching task and diagram test labels
     */
    public static String javaGradleStatementFixture(String source) {
        return source.replaceAll("\\b(test[\\w$]+)\\(\\)", "$1");
    }

    public Resource getResource(Path path) {
        return resolver.getResource("classpath:" + path.toString().replace('\\', '/'));
    }

    /**
     * Lists packaged files below a template directory.
     *
     * @param path classpath-relative template directory
     * @return matching resources, without filesystem checkout access
     */
    public Resource[] getFileResources(Path path) {
        try {
            return resolver.getResources("classpath*:" + path.toString().replace('\\', '/') + "/**/*");
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read packaged generation resources", e);
        }
    }
}
