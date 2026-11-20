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
    void recognizesAnAgentTakenFromTheConfigurationOfTheAresAgent() {
        String direct = "dependencies {\n    agent \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n}\ntest {\n    jvmArgs \"-javaagent:${configurations.agent.singleFile}\"\n}\n";
        String variable = "dependencies {\n    agent \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n}\ndef agentJar = configurations.agent\ntest {\n    jvmArgs \"-javaagent:${agentJar.singleFile}\"\n}\n";

        String continued = variable.replace("def agentJar = configurations.agent", "def agentJar =\n        configurations.agent");
        String chained = variable.replace("def agentJar = configurations.agent", "def agentJar = files()\nagentJar\n    .from(configurations.agent)");

        assertThat(BuildFileSyntax.attachesAresAgent(gradle(direct))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(direct.replace("${configurations.agent.singleFile}", "${ configurations.agent.singleFile }")))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(variable))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(continued))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(
                gradle(direct.replace("agent \"de.tum.cit.ase:ares:${aresVersion}:agent\"", "agent(\n        \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n    )")))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(chained))).isTrue();
        // The declaration may follow the brace that opens the block of dependencies on the same line.
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(direct.replace("dependencies {\n    agent \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n}",
                "dependencies { agent(\"de.tum.cit.ase:ares:${aresVersion}:agent\") }")))).isTrue();
    }

    @Test
    void checksEveryConfigurationThatDeclaresTheAresAgent() {
        String twoConfigurations = "dependencies {\n    smokeAgent \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n    testAgent \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n}\n"
                + "test {\n    jvmArgs \"-javaagent:${configurations.testAgent.singleFile}\"\n}\n";

        // The test JVM takes the agent from testAgent, the second configuration that declares the Ares agent.
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(twoConfigurations))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(twoConfigurations.replace("configurations.testAgent", "configurations.otherAgent")))).isFalse();
    }

    @Test
    void anotherAgentOrACommentIsNoAresAgent() {
        String jacoco = "dependencies {\n    aresAgent 'org.jacoco:org.jacoco.agent:0.8.16:runtime'\n}\ntest {\n    jvmArgs \"-javaagent:${jacocoJar}\"\n}\n";
        String commented = "dependencies {\n    // aresAgent \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n}\ntest {\n    // jvmArgs '-javaagent:ares.jar'\n}\n";
        String mavenComment = "<project><!-- <artifactId>ares</artifactId><classifier>agent</classifier> --><argLine>-javaagent:jacoco.jar</argLine></project>";

        String declaredButNotAttached = "dependencies {\n    testRuntimeOnly \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n}\ntest {\n    jvmArgs '-javaagent:jacocoagent.jar'\n}\n";
        String otherInterpolatedAgent = "dependencies {\n    testRuntimeOnly \"de.tum.cit.ase:ares:1.0:agent\"\n}\ndef runtimeFiles = configurations.testRuntimeOnly\n"
                + "def jacocoJar = file('jacocoagent.jar')\ntest {\n    jvmArgs \"-javaagent:${jacocoJar}\"\n}\n";
        String sameLine = otherInterpolatedAgent.replace("def runtimeFiles = configurations.testRuntimeOnly\ndef jacocoJar = file('jacocoagent.jar')",
                "def jacocoJar = file('jacocoagent.jar'); def runtimeFiles = configurations.testRuntimeOnly");
        String mentionedOnly = otherInterpolatedAgent.replace("def runtimeFiles = configurations.testRuntimeOnly", "println jacocoJar.name + configurations.testRuntimeOnly.name");
        String sharedConfiguration = "dependencies {\n    testRuntimeOnly \"de.tum.cit.ase:ares:1.0:agent\"\n    testRuntimeOnly \"org.jacoco:org.jacoco.agent:0.8.16:runtime\"\n}\n"
                + "test {\n    jvmArgs \"-javaagent:${configurations.testRuntimeOnly.find { it.name.startsWith('org.jacoco.agent') }}\"\n}\n";
        String reassigned = "dependencies {\n    aresAgent \"de.tum.cit.ase:ares:1.0:agent\"\n}\ndef agentJar = configurations.aresAgent\nagentJar = files('jacocoagent.jar')\n"
                + "test {\n    jvmArgs \"-javaagent:${agentJar.singleFile}\"\n}\n";
        String sameLineDependencies = "dependencies {\n    agentFiles \"de.tum.cit.ase:ares:1.0:agent\"; agentFiles \"org.jacoco:org.jacoco.agent:0.8.16:runtime\"\n}\n"
                + "test {\n    jvmArgs \"-javaagent:${configurations.agentFiles.find { it.name.startsWith('org.jacoco.agent') }}\"\n}\n";
        String aresNamedVariable = "dependencies {\n    aresAgent \"de.tum.cit.ase:ares:1.0:agent\"\n}\ndef aresAgentJar = files('jacocoagent.jar')\n"
                + "test {\n    jvmArgs \"-javaagent:${aresAgentJar.singleFile}\"\n}\n";
        String mavenJacocoOnly = "<project><artifactId>ares</artifactId><classifier>agent</classifier><argLine>-javaagent:${project.build.directory}/jacoco.jar</argLine></project>";

        assertThat(BuildFileSyntax.attachesAresAgent(gradle(jacoco))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(declaredButNotAttached))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(otherInterpolatedAgent))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(sameLine))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(sharedConfiguration))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(reassigned))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(sameLineDependencies))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(aresNamedVariable))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(aresNamedVariable.replace("${aresAgentJar.singleFile}", "${ aresAgentJar.singleFile }")))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(mentionedOnly))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(null, mavenJacocoOnly))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(commented))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(null, mavenComment))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(null, null))).isFalse();
    }
}
