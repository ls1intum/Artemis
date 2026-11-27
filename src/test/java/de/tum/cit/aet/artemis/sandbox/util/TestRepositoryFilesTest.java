package de.tum.cit.aet.artemis.sandbox.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.sandbox.domain.BuildFiles;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileContent;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileProblem;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxState;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxStatus;
import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReason;
import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReasonType;

/**
 * Tests how {@link TestRepositoryFiles} reads the Ares setup of an exercise-tests repository from the content of its files.
 */
class TestRepositoryFilesTest {

    private static final String TEST_CLASS = """
            package %s;

            import org.junit.jupiter.api.Test;

            import de.tum.cit.ase.ares.api.jupiter.Public;

            @Public
            @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
            class %s {

                @Test
                void runs() {
                }
            }
            """;

    private static final String POLICY = """
            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
              theSupervisedCodeUsesTheFollowingPackage: "de.example"
              theFollowingClassesAreTestClasses:
                - "de.example.SortingTest"
              theFollowingResourceAccessesArePermitted:
                regardingFileSystemInteractions: [ ]
                regardingNetworkConnections: [ ]
                regardingCommandExecutions: [ ]
                regardingThreadCreations: [ ]
                regardingPackageImports: [ ]
                regardingTimeouts: [ ]
            """;

    @TempDir
    private Path root;

    private void writeBuildFile() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '2.2.1'\n}\n", StandardCharsets.UTF_8);
    }

    private void writeSource(String relativePath, String source) throws IOException {
        FileUtils.writeStringToFile(root.resolve(relativePath).toFile(), source, StandardCharsets.UTF_8);
    }

    private void writeTestClass(String relativePath, String packageName, String className) throws IOException {
        FileUtils.writeStringToFile(root.resolve(relativePath).toFile(), TEST_CLASS.formatted(packageName, className), StandardCharsets.UTF_8);
    }

    private void assertUnsupported(UnsupportedReasonType type, String location) {
        SandboxState state = evaluateFiles();
        assertThat(state.status()).isEqualTo(SandboxStatus.UNSUPPORTED);
        assertThat(state.unsupportedReasons()).contains(new UnsupportedReason(location, type));
    }

    private static SandboxState evaluate(Map<String, String> sources, BuildFiles buildFiles) {
        return TestRepositoryFiles.evaluate(sources, _ -> PolicyFileContent.of(POLICY), buildFiles);
    }

    /**
     * Evaluates the files written to {@link #root} from their content, the way {@link TestRepositoryCommitFiles} evaluates a commit.
     */
    private SandboxState evaluateFiles() {
        try (Stream<Path> paths = Files.walk(root)) {
            Set<String> files = paths.filter(Files::isRegularFile).map(path -> root.relativize(path).toString().replace('\\', '/')).collect(Collectors.toSet());
            Map<String, String> sources = new TreeMap<>();
            for (String file : files) {
                if (TestRepositoryFiles.isJavaSourcePath(file, files::contains)) {
                    sources.put(file, readFile(file));
                }
            }
            return TestRepositoryFiles.evaluate(sources, path -> files.contains(path) ? PolicyFileContent.of(readFile(path)) : PolicyFileContent.failed(PolicyFileProblem.MISSING),
                    new BuildFiles(files.contains("build.gradle") ? readFile("build.gradle") : null, files.contains("pom.xml") ? readFile("pom.xml") : null));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readFile(String path) {
        try {
            return Files.readString(root.resolve(path));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void skipsBuildOutputInTheSameWayForCheckoutsAndCommittedTrees() {
        Set<String> files = Set.of("build.gradle", "module/pom.xml", "build/generated/GeneratedTest.java", "module/target/generated/ModuleTest.java",
                "test/de/example/build/BuildTest.java", "test/de/example/target/TargetTest.java", "other/build/OtherTest.java", ".gradle/cache/CachedTest.java",
                "test/de/example/SortingTest.java");

        assertThat(files.stream().filter(path -> TestRepositoryFiles.isJavaSourcePath(path, files::contains)).sorted()).containsExactly("other/build/OtherTest.java",
                "test/de/example/SortingTest.java", "test/de/example/build/BuildTest.java", "test/de/example/target/TargetTest.java");
    }

    @ParameterizedTest
    @ValueSource(strings = { "class SortingTest {|    @de.tum.cit.ase.ares.api.Policy\n    @Test\n    void sorts() {\n    }\n}",
            "class SortingTest {|    @Test\n    void runs() {\n    }\n\n" + "    @de.tum.cit.ase.ares.api.Policy\n    static class Nested {\n    }\n}" })
    void policiesOnMembersAreUnsupported(String classAndBody) throws IOException {
        writeBuildFile();
        String[] parts = classAndBody.split("\\|");
        writeSource("test/de/example/SortingTest.java", "package de.example;\n\nimport org.junit.jupiter.api.Test;\n\n@de.tum.cit.ase.ares.api.jupiter.Public\n"
                + "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")\n" + parts[0] + "\n" + parts[1] + "\n");

        assertUnsupported(UnsupportedReasonType.POLICY_NOT_ON_TOP_LEVEL_TYPE, "test/de/example/SortingTest.java");
    }

    @Test
    void severalTestTypesInOneFileAreUnsupported() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest") + TEST_CLASS.formatted("de.example", "OtherTest")
                .replace("package de.example;\n", "").replace("import org.junit.jupiter.api.Test;\n", "").replace("import de.tum.cit.ase.ares.api.jupiter.Public;\n", ""));

        assertUnsupported(UnsupportedReasonType.MULTIPLE_TOP_LEVEL_TYPES, "test/de/example/SortingTest.java");
    }

    @Test
    void aTestClassWithoutItsOwnPolicyIsUnsupported() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/HiddenTest.java", TEST_CLASS.formatted("de.example", "HiddenTest").replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n", "")
                .replace("jupiter.Public;", "jupiter.Hidden;").replace("@Public", "@Hidden"));

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.example.HiddenTest");
    }

    @Test
    void aSubclassThatOnlyInheritsThePolicyIsUnsupported() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/MoreSortingTest.java", "package de.example;\n\nclass MoreSortingTest extends SortingTest {\n}\n");

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.example.MoreSortingTest");
    }

    @Test
    void aSubclassOfATestClassWithAUnicodeNameIsATestClassToo() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingÜTest");
        writeSource("test/de/example/MoreSortingTest.java", "package de.example;\n\nclass MoreSortingTest extends SortingÜTest {\n}\n");

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.example.MoreSortingTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "@org.junit.jupiter.api.extension.ExtendWith(de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class)",
            "@de.tum.cit.ase.ares.api.jupiter.JupiterAresTest", "@Public$Tag", "@PublicÜ", "@de.tum.cit.ase.ares.api.jupiter.Public$Tag",
            "@ ExtendWith({ Other.class, de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class })" })
    void aTestClassThatDoesNotRegisterAresThroughItsMarkersIsUnsupported(String registration) throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/OtherTest.java", TEST_CLASS.formatted("de.example", "OtherTest").replace("@Public", registration));

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES, "de.example.OtherTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "@PublicTest$Tag", "@PublicTestÜ", "@de.tum.cit.ase.ares.api.jupiter.PublicTest$Tag" })
    void aMethodAnnotationWhoseNameOnlyStartsWithAnAresMarkerDoesNotRegisterAres(String annotation) throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/OtherTest.java", TEST_CLASS.formatted("de.example", "OtherTest").replace("jupiter.Public;", "jupiter.*;").replace("@Public\n", "")
                .replace("@Test\n", "@Test\n    " + annotation + "\n"));

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES, "de.example.OtherTest");
    }

    @Test
    void aMarkerTheFileDeclaresItselfDoesNotRegisterAres() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        // The declared annotation shadows the Ares one that the wildcard import provides.
        writeSource("test/de/example/OtherTest.java", TEST_CLASS.formatted("de.example", "OtherTest").replace("import de.tum.cit.ase.ares.api.jupiter.Public;",
                "import de.tum.cit.ase.ares.api.jupiter.*;\n\n@interface Public {\n}"));

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES, "de.example.OtherTest");
    }

    @Test
    void aTestClassThatOnlyUsesJupiterAresTestIsUnsupported() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/OtherTest.java", "package de.example;\n\n@de.tum.cit.ase.ares.api.jupiter.JupiterAresTest\nclass OtherTest {\n}\n");

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES, "test/de/example/OtherTest.java");
    }

    @Test
    void aSubclassInAnotherPackageFoundThroughAWildcardImportIsATestClass() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/other/MoreSortingTest.java", "package de.other;\n\nimport de.example.*;\n\nclass MoreSortingTest extends SortingTest {\n}\n");

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.other.MoreSortingTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "import de.bücher.SortingÜTest;", "import de.bücher.*;" })
    void aSubclassInAnotherPackageOfAnImportedTestClassWithAUnicodeNameIsATestClass(String importDeclaration) throws IOException {
        writeBuildFile();
        writeTestClass("test/de/buecher/SortingTest.java", "de.bücher", "SortingÜTest");
        writeSource("test/de/other/MoreSortingTest.java", "package de.other;\n\n" + importDeclaration + "\n\nclass MoreSortingTest extends SortingÜTest {\n}\n");

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.other.MoreSortingTest");
    }

    @Test
    void plainJUnitTestsAreUnsupported() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java",
                "package de.example;\n\nimport org.junit.jupiter.api.Test;\n\nclass SortingTest {\n\n    @Test\n    void sorts() {\n    }\n}\n");

        assertUnsupported(UnsupportedReasonType.NO_ARES_2, "de.example.SortingTest");
    }

    @Test
    void ares1TestsAreUnsupported() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "dependencies {\n    testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.15.0'\n}\n",
                StandardCharsets.UTF_8);
        writeSource("test/de/example/SortingTest.java", "package de.example;\n\nimport org.junit.jupiter.api.Test;\n\nimport de.tum.in.test.api.jupiter.Public;\n\n"
                + "@Public\nclass SortingTest {\n\n    @Test\n    void sorts() {\n    }\n}\n");

        assertUnsupported(UnsupportedReasonType.ARES_1, "build.gradle");
        assertUnsupported(UnsupportedReasonType.NO_ARES_VERSION_PINNED, "build.gradle");
    }

    @ParameterizedTest
    @ValueSource(strings = { "../outside.yaml", "/etc/policy.yaml", ".git/config", "C:\\policy.yaml", "", "  ", "a\0b.yaml", "\\policy.yaml", ".", ".git", "..",
            "a/../../policy.yaml", "x\\..\\.git\\config", "a\\..\\..\\policy.yaml", ".GIT/config", ".Git", "./.gIt/hooks/pre-commit.yaml" })
    void rejectsPolicyPathsOutsideTheRepository(String path) {
        assertThat(TestRepositoryFiles.validatePolicyPath(path)).isEmpty();
        assertThat(TestRepositoryFiles.validatePolicyPath("./policies/../SecurityPolicy.yaml")).contains("SecurityPolicy.yaml");
        // A backslash separates names, so the path is normalized as the checkout resolves it.
        assertThat(TestRepositoryFiles.validatePolicyPath(".\\policies\\..\\SecurityPolicy.yaml")).contains("SecurityPolicy.yaml");
    }

    @Test
    void readsTheAresVersionFromBuildFileContents() {
        assertThat(TestRepositoryFiles.parseAresVersion("ext {\n    aresVersion = '2.3.0'\n}\n", null)).contains("2.3.0");
        assertThat(TestRepositoryFiles.parseAresVersion(null, "<properties><ares.version>2.2.1</ares.version></properties>")).contains("2.2.1");
        assertThat(TestRepositoryFiles.parseAresVersion(null, "<project/>")).isEmpty();
    }

    @Test
    void ignoresAresVersionsInComments() {
        String gradleBuild = "// aresVersion = '2.3.0'\n/* aresVersion = \"9.9.9\" */\nrepositories { maven { url 'https://example.org/maven' } }\ndef aresVersion = '2.2.1'\n";
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        assertThat(TestRepositoryFiles.parseAresVersion("// aresVersion = '2.3.0'\n", null)).isEmpty();
        assertThat(TestRepositoryFiles.parseAresVersion(null, "<!-- <ares.version>2.3.0</ares.version> -->\n<ares.version>2.2.1</ares.version>")).contains("2.2.1");
    }

    @Test
    void ignoresAresVersionsInGradleStrings() {
        assertThat(TestRepositoryFiles.parseAresVersion("def example = \"aresVersion = '2.3.0'\"\next { aresVersion = '2.2.1' }\n", null)).contains("2.2.1");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "ext { aresVersion = '2.2.1' } // Replaced artemis-java-test-sandbox with Ares 2\\n||false",
            "/* artemis-java-test-sandbox */ ext { aresVersion = '2.2.1' }\\n||false",
            "ext { aresVersion = '2.2.1' }\\ndependencies { testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0' }\\n||true",
            "ext { aresVersion = '2.2.1' }\\ndependencies { testImplementation group: 'de.tum.in.ase', name: 'artemis-java-test-sandbox' }\\n||true",
            "|<!-- replaced artemis-java-test-sandbox --><properties><ares.version>2.2.1</ares.version></properties>|false",
            "|<properties><ares.version>2.2.1</ares.version></properties><dependency><artifactId> artemis-java-test-sandbox </artifactId></dependency>|true" })
    void onlyADependencyOnAres1MakesTheBuildUnsupported(String gradleBuild, String mavenPom, boolean ares1) {
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest"));
        String gradle = gradleBuild == null ? null : gradleBuild.replace("\\n", "\n");
        UnsupportedReason reason = new UnsupportedReason(gradle != null ? "build.gradle" : "pom.xml", UnsupportedReasonType.ARES_1);

        // A comment that mentions the library, e.g. after the migration to Ares 2, declares no dependency on it.
        if (ares1) {
            assertThat(evaluate(sources, new BuildFiles(gradle, mavenPom)).unsupportedReasons()).contains(reason);
        }
        else {
            assertThat(evaluate(sources, new BuildFiles(gradle, mavenPom)).unsupportedReasons()).noneMatch(unsupported -> unsupported.type() == UnsupportedReasonType.ARES_1);
        }
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void readsTheAresVersionAfterALongCommentFullOfPinsQuickly() {
        // A pin-shaped line in a comment is no assignment the build reads, so it is not followed to the end of the comment again for every such line.
        String gradleBuild = "/*\n" + "aresVersion = '9.9.9' /*\n".repeat(10_000) + "*/\next { aresVersion = '2.2.1' }\n";
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
    }

    @ParameterizedTest
    @ValueSource(strings = { "ext { aresVersion = '2.2.1' }\n", "ext.aresVersion = '2.2.1'; // Ares 2\n", "ext.aresVersion = \"2.2.1\" /* Ares 2 */\n", "ext.aresVersion = '2.2.1'",
            "ext { aresVersion = '2.2.1' /* Ares 2 */ /* pinned */ }\n" })
    void aCompleteLiteralIsAPin(String gradleBuild) {
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
    }

    @Test
    void aMavenProfileThatPinsTheAresVersionMakesTheBuildUnsupported() {
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest"));
        String rootPin = "<properties><ares.version>2.2.1</ares.version></properties>";
        String profile = "<profiles><profile><activation><activeByDefault>true</activeByDefault></activation>"
                + "<properties><ares.version>2.3.0</ares.version></properties></profile></profiles>";
        var reason = new UnsupportedReason("pom.xml", UnsupportedReasonType.ARES_VERSION_IN_MAVEN_PROFILE);

        // While the profile is active, Maven uses its version instead of the one of the POM, which Artemis would read and change.
        assertThat(evaluate(sources, new BuildFiles(null, rootPin + profile)).unsupportedReasons()).containsExactly(reason);
        assertThat(evaluate(sources, new BuildFiles(null, profile)).unsupportedReasons()).containsExactly(reason);
        // XML allows whitespace before the > of a tag.
        String spacedProfile = profile.replace("<profiles>", "<profiles >").replace("</profiles>", "</profiles\n>");
        assertThat(evaluate(sources, new BuildFiles(null, rootPin + spacedProfile)).unsupportedReasons()).containsExactly(reason);
        // A reference to another property replaces the version of the POM as well, e.g. with next.ares.version = 2.3.0.
        String referencingProfile = profile.replace("<ares.version>2.3.0</ares.version>", "<ares.version>${next.ares.version}</ares.version>");
        assertThat(evaluate(sources, new BuildFiles(null, rootPin + referencingProfile)).unsupportedReasons()).containsExactly(reason);
        String spacedProperty = profile.replace("<ares.version>2.3.0</ares.version>", "<ares.version >2.3.0</ares.version\n>");
        assertThat(evaluate(sources, new BuildFiles(null, rootPin + spacedProperty)).unsupportedReasons()).containsExactly(reason);
        // A Gradle build that pins the version is the one the exercise uses, and a profile in a comment pins nothing.
        assertThat(evaluate(sources, new BuildFiles("ext { aresVersion = '2.2.1' }\n", profile)).unsupportedReasons()).isEmpty();
        assertThat(evaluate(sources, new BuildFiles(null, rootPin + "<!-- " + profile + " -->")).unsupportedReasons()).isEmpty();
    }

    @Test
    void namesTheBuildFileThatRefersToAres1OrPinsNoVersion() {
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest"));
        String ares1Pom = "<dependency><artifactId>artemis-java-test-sandbox</artifactId></dependency>";
        String ares1Gradle = "dependencies { testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0' }\n";

        assertThat(evaluate(sources, new BuildFiles(null, ares1Pom)).unsupportedReasons()).containsExactly(new UnsupportedReason("pom.xml", UnsupportedReasonType.ARES_1),
                new UnsupportedReason("pom.xml", UnsupportedReasonType.NO_ARES_VERSION_PINNED));
        assertThat(evaluate(sources, new BuildFiles(ares1Gradle, null)).unsupportedReasons()).containsExactly(new UnsupportedReason("build.gradle", UnsupportedReasonType.ARES_1),
                new UnsupportedReason("build.gradle", UnsupportedReasonType.NO_ARES_VERSION_PINNED));
        assertThat(evaluate(sources, new BuildFiles(null, "<project/>")).unsupportedReasons())
                .containsExactly(new UnsupportedReason("pom.xml", UnsupportedReasonType.NO_ARES_VERSION_PINNED));
        assertThat(evaluate(sources, new BuildFiles(null, null)).unsupportedReasons())
                .containsExactly(new UnsupportedReason("build.gradle", UnsupportedReasonType.NO_ARES_VERSION_PINNED));
    }

    @Test
    void aBlankPolicyPathNamesNoPolicyFile() {
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("SecurityPolicy.yaml", " "));

        SandboxState state = evaluate(sources, new BuildFiles("ext {\n    aresVersion = '2.2.1'\n}\n", null));

        assertThat(state.status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(state.policyFiles()).isEmpty();
    }
}
