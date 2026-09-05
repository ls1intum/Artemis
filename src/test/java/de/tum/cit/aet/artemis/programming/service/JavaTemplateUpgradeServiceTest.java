package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.util.LocalVCRepositoryTestService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * Tests the template upgrade that an instructor triggers when importing a Java exercise.
 * <p>
 * An exercise imported from an older course still carries the build configuration it was written with, which no longer
 * builds against the current Artemis templates: JUnit 4 in place of Ares, dependencies that moved into Ares, analyzer
 * plugins that either have to be added or removed depending on whether the new exercise enables static code analysis,
 * and test classes that Artemis has since replaced. The upgrade rewrites all of that in place, so what it produces is
 * what the imported exercise will be built with.
 */
class JavaTemplateUpgradeServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "javatemplateupgrade";

    private static final String ARES_ARTIFACT_ID = "artemis-java-test-sandbox";

    /**
     * The project object model of an exercise written before Ares: JUnit 4 as the test framework, two libraries that
     * have since moved into Ares, and the analyzer plugins of the time.
     */
    private static final String LEGACY_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>de.test</groupId>
                <artifactId>Legacy-Tests</artifactId>
                <version>1.0</version>
                <properties>
                    <scaConfigDirectory>${project.basedir}/staticCodeAnalysisConfig</scaConfigDirectory>
                    <analyzeTests>false</analyzeTests>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.12</version>
                    </dependency>
                    <dependency>
                        <groupId>org.json</groupId>
                        <artifactId>json</artifactId>
                        <version>20180813</version>
                    </dependency>
                    <dependency>
                        <groupId>me.xdrop</groupId>
                        <artifactId>fuzzywuzzy</artifactId>
                        <version>1.2.0</version>
                    </dependency>
                </dependencies>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.5.1</version>
                        </plugin>
                        <plugin>
                            <groupId>com.github.spotbugs</groupId>
                            <artifactId>spotbugs-maven-plugin</artifactId>
                            <version>4.0.0</version>
                        </plugin>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-checkstyle-plugin</artifactId>
                            <version>3.1.0</version>
                        </plugin>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-pmd-plugin</artifactId>
                            <version>3.13.0</version>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;

    @Autowired
    private JavaTemplateUpgradeService javaTemplateUpgradeService;

    @Autowired
    private LocalVCRepositoryTestService localVCRepositoryTestService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private ProgrammingExercise programmingExercise;

    @AfterEach
    void tearDown() {
        // The upgrade checks the repositories out and leaves the checkouts behind, where the running server would find them warm. Fixture work must not do that.
        for (RepositoryType repositoryType : List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS)) {
            gitService.deleteLocalRepository(
                    localVCRepositoryTestService.repositoryUri(programmingExercise.getProjectKey(), programmingExercise.generateRepositoryName(repositoryType)));
        }
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        // The upgrade reads the build configuration and the repository URI of every repository type, so both the build config and the participations have to be loaded rather
        // than left as lazy proxies.
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void upgradeTemplate_replacesJUnitWithAresAndDropsTheLibrariesThatMovedIntoIt() {
        seedTestRepository(Map.of("pom.xml", LEGACY_POM));

        javaTemplateUpgradeService.upgradeTemplate(programmingExercise);

        String upgradedPom = readTestRepositoryFile("pom.xml");
        assertThat(upgradedPom).as("JUnit 4 is replaced, because the current templates run the tests through Ares").doesNotContain("<artifactId>junit</artifactId>");
        assertThat(upgradedPom).as("Ares is added as the test framework").contains(ARES_ARTIFACT_ID);
        assertThat(upgradedPom).as("the libraries that moved into Ares are dropped rather than kept alongside it").doesNotContain("fuzzywuzzy")
                .doesNotContain("<artifactId>json</artifactId>");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void upgradeTemplate_withoutStaticCodeAnalysis_removesTheAnalyzerPluginsAndTheirConfiguration() {
        // The exercise this is imported into does not run static code analysis, so the plugins its build would invoke have to go, along with the configuration they read.
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        seedTestRepository(Map.of("pom.xml", LEGACY_POM, "staticCodeAnalysisConfig/checkstyle-configuration.xml", "<module name=\"Checker\"/>"));

        javaTemplateUpgradeService.upgradeTemplate(programmingExercise);

        String upgradedPom = readTestRepositoryFile("pom.xml");
        assertThat(upgradedPom).as("no analyzer plugin is left in the build").doesNotContain("spotbugs-maven-plugin").doesNotContain("maven-checkstyle-plugin")
                .doesNotContain("maven-pmd-plugin");
        assertThat(upgradedPom).as("the properties the analyzers read are removed with them").doesNotContain("scaConfigDirectory").doesNotContain("analyzeTests");
        assertThat(testRepositoryFiles()).as("the analyzer configuration directory is removed as well").noneMatch(path -> path.startsWith("staticCodeAnalysisConfig/"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void upgradeTemplate_withStaticCodeAnalysis_keepsTheAnalyzersAndAddsTheirConfiguration() {
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        seedTestRepository(Map.of("pom.xml", LEGACY_POM));

        javaTemplateUpgradeService.upgradeTemplate(programmingExercise);

        String upgradedPom = readTestRepositoryFile("pom.xml");
        assertThat(upgradedPom).as("the analyzers an exercise with static code analysis needs are kept").contains("spotbugs-maven-plugin").contains("maven-checkstyle-plugin")
                .contains("maven-pmd-plugin");
        assertThat(upgradedPom).as("the properties the analyzers read are set").contains("scaConfigDirectory").contains("analyzeTests");
        // Without these files the analyzers have nothing to run against, so the build would report no issues at all rather than fail.
        assertThat(testRepositoryFiles()).as("the current analyzer configuration is copied into the repository").contains("staticCodeAnalysisConfig/checkstyle-configuration.xml",
                "staticCodeAnalysisConfig/pmd-configuration.xml", "staticCodeAnalysisConfig/spotbugs-exclusions.xml");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void upgradeTemplate_removesTheTestClassesArtemisReplaced() {
        // StructuralTest.java and the testUtils package were superseded by the structural test classes Artemis ships. Leaving them behind breaks the build with duplicate classes.
        seedTestRepository(Map.of("pom.xml", LEGACY_POM, "StructuralTest.java", "public class StructuralTest {}", "testUtils/Helper.java", "public class Helper {}",
                "AttributeTest.java", "// the outdated copy of the Artemis test class"));

        javaTemplateUpgradeService.upgradeTemplate(programmingExercise);

        List<String> files = testRepositoryFiles();
        assertThat(files).as("the superseded test class is deleted").noneMatch(path -> path.endsWith("StructuralTest.java"));
        assertThat(files).as("the superseded test utilities are deleted").noneMatch(path -> path.startsWith("testUtils/"));
        assertThat(readTestRepositoryFile("AttributeTest.java")).as("a test class Artemis ships is overwritten with the current version")
                .isNotEqualTo("// the outdated copy of the Artemis test class").contains("class AttributeTest");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void upgradeTemplate_forASequentialTestRunExercise_changesNothing() {
        // Sequential test runs split the repository into build stages with a project file each, which this upgrade cannot rewrite, so it has to leave the exercise alone
        // rather than produce a half-upgraded repository.
        programmingExercise.getBuildConfig().setSequentialTestRuns(true);
        seedTestRepository(Map.of("pom.xml", LEGACY_POM));

        javaTemplateUpgradeService.upgradeTemplate(programmingExercise);

        assertThat(readTestRepositoryFile("pom.xml")).as("a sequential test run exercise is left exactly as it was").isEqualTo(LEGACY_POM);
    }

    private void seedTestRepository(Map<String, String> files) {
        localVCRepositoryTestService.writeFilesAndPush(testRepositoryUri(), files, "the state of the exercise before the upgrade");
    }

    private LocalVCRepositoryUri testRepositoryUri() {
        return localVCRepositoryTestService.repositoryUri(programmingExercise.getProjectKey(), programmingExercise.generateRepositoryName(RepositoryType.TESTS));
    }

    private String readTestRepositoryFile(String filePath) {
        return localVCRepositoryTestService.readFile(testRepositoryUri(), filePath);
    }

    private List<String> testRepositoryFiles() {
        return localVCRepositoryTestService.listFilePaths(testRepositoryUri());
    }
}
