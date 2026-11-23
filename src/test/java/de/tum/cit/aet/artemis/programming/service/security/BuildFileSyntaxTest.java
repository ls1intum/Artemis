package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BuildFileSyntaxTest {

    private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates", "java");

    private static final String MAVEN_TEMPLATE = "test/maven/projectTemplate/pom.xml";

    private static SecurityPolicyRepositoryFiles.BuildFiles gradle(String content) {
        return new SecurityPolicyRepositoryFiles.BuildFiles(content, null);
    }

    private static SecurityPolicyRepositoryFiles.BuildFiles mavenBuild(String content) {
        return new SecurityPolicyRepositoryFiles.BuildFiles(null, content);
    }

    /** Adds the setting to the configuration of Surefire only, not to the properties of the POM or the configuration of another plugin. */
    private static String inSurefireConfiguration(String pom, String setting) {
        String changed = pom.replaceFirst("(<artifactId>maven-surefire-plugin</artifactId>\\s*+<version>[^<]*+</version>\\s*+<configuration>)", "$1" + setting);
        assertThat(changed).isNotEqualTo(pom);
        return changed;
    }

    @Test
    void recognizesTheAresAgentOfTheTemplates() throws IOException {
        String gradle = Files.readString(TEMPLATES.resolve("test/gradle/projectTemplate/build.gradle"), StandardCharsets.UTF_8);
        String maven = Files.readString(TEMPLATES.resolve("test/maven/projectTemplate/pom.xml"), StandardCharsets.UTF_8);

        assertThat(BuildFileSyntax.attachesAresAgent(gradle(gradle))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(null, maven))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "<forkCount>0</forkCount>", "<forkCount> 0C </forkCount>" })
    void theAgentOfTheArgLineDoesNotCountIfSurefireRunsTheTestsInTheMavenJvm(String forkCount) throws IOException {
        String maven = Files.readString(TEMPLATES.resolve(MAVEN_TEMPLATE), StandardCharsets.UTF_8);

        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(inSurefireConfiguration(maven, forkCount)))).isFalse();
        // A forked test JVM gets the argLine, and a forkCount in a comment has no effect.
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(inSurefireConfiguration(maven, "<forkCount>1</forkCount>")))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(inSurefireConfiguration(maven, "<!-- " + forkCount + " -->")))).isTrue();
    }

    @Test
    void theForkCountPropertyAlsoRunsTheTestsInTheMavenJvm() throws IOException {
        String maven = Files.readString(TEMPLATES.resolve(MAVEN_TEMPLATE), StandardCharsets.UTF_8);
        String inProcess = maven.replaceFirst("<properties>", "<properties><forkCount>0</forkCount>");

        assertThat(inProcess).isNotEqualTo(maven);
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(inProcess))).isFalse();
    }

    @Test
    void aForkCountInAProfileCountsOnlyIfTheProfileCanApplyOnItsOwn() throws IOException {
        String maven = Files.readString(TEMPLATES.resolve(MAVEN_TEMPLATE), StandardCharsets.UTF_8);
        String optIn = "<profiles><profile><id>inProcess</id><properties><forkCount>0</forkCount></properties></profile></profiles></project>";
        String byDefault = "<profiles><profile><id>inProcess</id><activation><activeByDefault>true</activeByDefault></activation>"
                + "<properties><forkCount>0</forkCount></properties></profile></profiles></project>";

        // The build of the templates names no profile, so a profile without an activation does not apply to it.
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", optIn)))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", byDefault)))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "add('aresAgent', \"de.tum.cit.ase:ares:${aresVersion}:agent\")", "dependencies.add(\"aresAgent\", \"de.tum.cit.ase:ares:${aresVersion}:agent\")" })
    void recognizesTheAgentOfTheTemplatesDeclaredWithAdd(String declaration) throws IOException {
        String gradle = Files.readString(TEMPLATES.resolve("test/gradle/projectTemplate/build.gradle"), StandardCharsets.UTF_8);
        String added = gradle.replace("aresAgent \"de.tum.cit.ase:ares:${aresVersion}:agent\"", declaration);
        assertThat(added).isNotEqualTo(gradle);

        // add of the dependency handler adds the same dependency to the same configuration, which the test JVM takes its agent from.
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(added))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(added.replace("configurations.aresAgent", "configurations.otherAgent")))).isFalse();
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
    void aJavaAgentInAPropertyCountsOnlyIfTheArgLineOfSurefireReadsIt() throws IOException {
        String maven = Files.readString(TEMPLATES.resolve(MAVEN_TEMPLATE), StandardCharsets.UTF_8);
        String agent = "\"-javaagent:${project.build.directory}/ares/ares-agent.jar\"";
        String withoutAgent = maven.replace(agent, "");
        assertThat(withoutAgent).isNotEqualTo(maven);
        String unusedProperty = withoutAgent.replaceFirst("<properties>", "<properties><aresAgentArgument>" + Matcher.quoteReplacement(agent) + "</aresAgentArgument>");
        String readProperty = unusedProperty.replace("@{argLine}", "@{argLine} ${aresAgentArgument}");

        // A property that no argLine reads does not reach the test JVM.
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(unusedProperty))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(readProperty))).isTrue();
        // The argLine property reaches the test JVM through @{argLine} in the configuration, and not without it.
        String argLineProperty = withoutAgent.replace("<argLine></argLine>", "<argLine>" + agent + "</argLine>");
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(argLineProperty))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(argLineProperty.replace("@{argLine}", "")))).isFalse();
        // The argLine of another plugin, e.g. Failsafe, does not reach the tests Surefire runs.
        String failsafe = "<artifactId>maven-failsafe-plugin</artifactId><configuration><argLine>" + agent + "</argLine></configuration></plugin><plugin>";
        String failsafeAgent = withoutAgent.replace("<artifactId>maven-surefire-plugin</artifactId>", failsafe + "<artifactId>maven-surefire-plugin</artifactId>");
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(failsafeAgent))).isFalse();
    }

    @Test
    void anArgLineOutsideSurefireOrThatAProfileMayReplaceDoesNotCount() throws IOException {
        String maven = Files.readString(TEMPLATES.resolve(MAVEN_TEMPLATE), StandardCharsets.UTF_8);
        String argLine = "<argLine>-javaagent:ares-agent.jar</argLine>";
        String withoutAgent = maven.replace("\"-javaagent:${project.build.directory}/ares/ares-agent.jar\"", "");
        String optIn = "<profiles><profile><id>other</id><build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId><configuration>" + argLine
                + "</configuration></plugin></plugins></build></profile></profiles></project>";
        String byDefault = optIn.replace("<id>other</id>", "<id>other</id><activation><activeByDefault>true</activeByDefault></activation>");

        // An argLine that is neither the configuration of Surefire nor its property reaches no test JVM.
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(withoutAgent.replace("</project>", "<reporting>" + argLine + "</reporting></project>")))).isFalse();
        // A profile the build does not name changes nothing, but one that can apply on its own may replace the argLine of the POM.
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", optIn)))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", byDefault)))).isFalse();
        // An empty argLine of such a profile replaces the one of the POM as well.
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", byDefault.replace(argLine, "<argLine/>"))))).isFalse();
    }

    @Test
    void aPropertyOfTheArgLineThatAProfileMayReplaceDoesNotCount() throws IOException {
        String template = Files.readString(TEMPLATES.resolve(MAVEN_TEMPLATE), StandardCharsets.UTF_8);
        String agent = "\"-javaagent:${project.build.directory}/ares/ares-agent.jar\"";
        // The argLine of Surefire takes the agent from a property, which a profile may set to another agent.
        String maven = template.replace(agent, "${aresAgent}").replaceFirst("<properties>", "<properties>\n<aresAgent>" + Matcher.quoteReplacement(agent) + "</aresAgent>");
        String optIn = "<profiles><profile><id>other</id><properties><aresAgent>-javaagent:other-agent.jar</aresAgent></properties></profile></profiles></project>";
        String byDefault = optIn.replace("<id>other</id>", "<id>other</id><activation><activeByDefault>true</activeByDefault></activation>");

        assertThat(maven).isNotEqualTo(template).contains("<aresAgent>");
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", optIn)))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", byDefault)))).isFalse();
        // A property that the argLine does not read changes nothing.
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", byDefault.replace("aresAgent>", "otherAgent>"))))).isTrue();
        // Maven reads an empty element as an empty value, which replaces the agent as well, also with attributes like combine.self.
        String emptied = byDefault.replace("<aresAgent>-javaagent:other-agent.jar</aresAgent>", "<aresAgent/>");
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", emptied)))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("</project>", byDefault.replace("<aresAgent>", "<aresAgent combine.self=\"override\">"))))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(mavenBuild(maven.replace("<aresAgent>" + agent + "</aresAgent>", "<aresAgent/>")))).isFalse();
    }

    @Test
    void aGradleAgentCountsOnlyIfATaskReceivesIt() throws IOException {
        String gradle = Files.readString(TEMPLATES.resolve("test/gradle/projectTemplate/build.gradle"), StandardCharsets.UTF_8);
        String notRegistered = gradle.replace("jvmArgumentProviders.add(aresJvmArguments)", "");
        String direct = "dependencies {\n    agent \"de.tum.cit.ase:ares:${aresVersion}:agent\"\n}\n";
        String listed = direct + "test {\n    jvmArgs += [\n        '--enable-preview',\n        \"-javaagent:${configurations.agent.singleFile}\"\n    ]\n}\n";

        assertThat(notRegistered).isNotEqualTo(gradle);
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(notRegistered))).isFalse();
        // The registration may put its argument on the next line.
        assertThat(BuildFileSyntax
                .attachesAresAgent(gradle(gradle.replace("jvmArgumentProviders.add(aresJvmArguments)", "jvmArgumentProviders.add(\n        aresJvmArguments\n    )")))).isTrue();
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(direct + "def agentArgument = \"-javaagent:${configurations.agent.singleFile}\"\n"))).isFalse();
        // The arguments of jvmArgs may continue on the following lines.
        assertThat(BuildFileSyntax.attachesAresAgent(gradle(listed))).isTrue();
    }

    @Test
    void onlyTheBuildFileOfTheBuildToolThatPinsAresCounts() {
        String gradleWithoutAgent = "ext { aresVersion = '2.2.1' }\n";
        String mavenWithAgent = "<properties><ares.version>2.2.1</ares.version></properties><artifactId>ares</artifactId><classifier>agent</classifier>"
                + "<build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId><configuration>"
                + "<argLine>-javaagent:${settings.localRepository}/de/tum/cit/ase/ares/${ares.version}/ares-${ares.version}-agent.jar</argLine>"
                + "</configuration></plugin></plugins></build>";
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(null, mavenWithAgent))).isTrue();

        // The Gradle build pins the Ares version, so it is the build that runs; the pom.xml next to it does not attach the agent to the tests.
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(gradleWithoutAgent, mavenWithAgent))).isFalse();
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles("repositories { mavenCentral() }\n", mavenWithAgent))).isTrue();
        // Pins of different versions leave the version unknown, but the Gradle build that sets them still runs instead of the pom.xml.
        String conflictingPins = "project.ext.aresVersion = '2.3.0'\nproject.ext.aresVersion = '2.2.1'\n";
        assertThat(BuildFileSyntax.attachesAresAgent(new SecurityPolicyRepositoryFiles.BuildFiles(conflictingPins, mavenWithAgent))).isFalse();
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
