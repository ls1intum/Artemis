package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BuildFileSyntaxTest {

    private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates", "java");

    private static SecurityPolicyRepositoryFiles.BuildFiles gradle(String content) {
        return new SecurityPolicyRepositoryFiles.BuildFiles(content, null);
    }

    @Test
    void recognizesTheAresAgentOfTheTemplates() throws IOException {
        String gradle = Files.readString(TEMPLATES.resolve("test/gradle/projectTemplate/build.gradle"), StandardCharsets.UTF_8);
        String maven = Files.readString(TEMPLATES.resolve("test/maven/projectTemplate/pom.xml"), StandardCharsets.UTF_8);

        assertThat(BuildFileSyntax.attachesAresAgent(gradle(gradle))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(null, maven))).isTrue();
    }

    @Test
    void anotherAgentOrACommentIsNoAresAgent() {
        String jacoco = "dependencies {\n    aresAgent 'org.jacoco:org.jacoco.agent:0.8.16:runtime'\n}\ntest {\n    jvmArgs \"-javaagent:${jacocoJar}\"\n}\n";
        String commented = "dependencies {\n    // aresAgent \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n}\ntest {\n    // jvmArgs '-javaagent:ares.jar'\n}\n";
        String mavenComment = "<project><!-- <artifactId>ares</artifactId><classifier>agent</classifier> --><argLine>-javaagent:jacoco.jar</argLine></project>";

        String declaredButNotAttached = "dependencies {\n    testRuntimeOnly \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n}\ntest {\n    jvmArgs '-javaagent:jacocoagent.jar'\n}\n";
        String mavenJacocoOnly = "<project><artifactId>ares</artifactId><classifier>agent</classifier><argLine>-javaagent:${project.build.directory}/jacoco.jar</argLine></project>";

        assertThat(BuildFileSyntax.attachesAresAgent(gradle(jacoco))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(declaredButNotAttached))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(null, mavenJacocoOnly))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(commented))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(null, mavenComment))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(null, null))).isFalse();
    }
}
