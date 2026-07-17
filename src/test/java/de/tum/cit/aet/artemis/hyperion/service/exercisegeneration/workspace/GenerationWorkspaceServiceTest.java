package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class GenerationWorkspaceServiceTest {

    @Test
    void prepareRepositoryForGeneration_removesExerciseArtifactsButKeepsBuildConfiguration() throws Exception {
        Path root = tempFileUtilService().createTempDirectory("hyperion-generation-seed");
        try {
            Files.createDirectories(root.resolve("src/de/test"));
            FileUtils.writeStringToFile(root.resolve("src/de/test/BubbleSort.java").toFile(), "class BubbleSort {}", StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(root.resolve("pom.xml").toFile(), "<project />", StandardCharsets.UTF_8);

            GenerationWorkspaceService.prepareRepositoryForMode(root, RepositoryType.SOLUTION, GenerationMode.GENERATE);

            assertThat(root.resolve("src")).doesNotExist();
            assertThat(root.resolve("pom.xml")).hasContent("<project />");
        }
        finally {
            FileUtils.deleteDirectory(root.toFile());
        }
    }

    @Test
    void prepareTestRepositoryForGeneration_removesConventionalAndCategorizedTests() throws Exception {
        Path root = tempFileUtilService().createTempDirectory("hyperion-generation-tests-seed");
        try {
            for (String testRoot : Set.of("test", "behavior/test", "structural/test")) {
                Path test = root.resolve(testRoot).resolve("de/test/BubbleSortTest.java");
                Files.createDirectories(test.getParent());
                FileUtils.writeStringToFile(test.toFile(), "class BubbleSortTest {}", StandardCharsets.UTF_8);
            }
            Files.createDirectories(root.resolve("behavior"));
            FileUtils.writeStringToFile(root.resolve("behavior/build.gradle").toFile(), "plugins {}", StandardCharsets.UTF_8);

            GenerationWorkspaceService.prepareRepositoryForMode(root, RepositoryType.TESTS, GenerationMode.GENERATE);

            assertThat(root.resolve("test")).doesNotExist();
            assertThat(root.resolve("behavior/test")).doesNotExist();
            assertThat(root.resolve("structural/test")).doesNotExist();
            assertThat(root.resolve("behavior/build.gradle")).hasContent("plugins {}");
        }
        finally {
            FileUtils.deleteDirectory(root.toFile());
        }
    }

    @Test
    void prepareRepositoryForAdaptation_preservesExistingArtifacts() throws Exception {
        Path root = tempFileUtilService().createTempDirectory("hyperion-adaptation-seed");
        try {
            Path source = root.resolve("src/de/test/Inventory.java");
            Files.createDirectories(source.getParent());
            FileUtils.writeStringToFile(source.toFile(), "class Inventory {}", StandardCharsets.UTF_8);

            GenerationWorkspaceService.prepareRepositoryForMode(root, RepositoryType.SOLUTION, GenerationMode.ADAPT);

            assertThat(source).hasContent("class Inventory {}");
        }
        finally {
            FileUtils.deleteDirectory(root.toFile());
        }
    }

    @Test
    void sessionSpec_disablesNetworkEgressByDefault() {
        ProgrammingLanguageConfiguration languageConfiguration = mock(ProgrammingLanguageConfiguration.class);
        when(languageConfiguration.getImage(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_GRADLE))).thenReturn("java-image");
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), languageConfiguration, mock(), mock(), tempFileUtilService());

        var spec = service.sessionSpec(exercise);

        assertThat(spec.image()).isEqualTo("java-image");
        assertThat(spec.runConfig().network()).isEqualTo("none");
    }

    @Test
    void readReferenceSample_closesResourceStreams() throws Exception {
        ResourceLoaderService resourceLoaderService = mock(ResourceLoaderService.class);
        Resource resource = mock(Resource.class);
        TrackingInputStream input = new TrackingInputStream("class Example {}".getBytes(StandardCharsets.UTF_8));
        when(resource.getURI()).thenReturn(java.net.URI.create("file:/templates/hyperion/reference/java/tests/test/Example.java"));
        when(resource.getInputStream()).thenReturn(input);
        when(resourceLoaderService.getFileResources(any(Path.class))).thenAnswer(invocation -> {
            Path path = invocation.getArgument(0);
            return path.endsWith("test") ? new Resource[] { resource } : new Resource[0];
        });
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_MAVEN);
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), resourceLoaderService, tempFileUtilService());

        service.readReferenceSample(exercise);
        assertThat(input.closed).isTrue();
    }

    @Test
    void readReferenceSample_buildsACompleteCuratedJavaExercise() throws Exception {
        ResourceLoaderService resourceLoaderService = mock(ResourceLoaderService.class);
        Resource statement = resource("file:/templates/hyperion/reference/java/problem-statement.md", "Problem statement");
        when(resourceLoaderService.getResource(Path.of("templates/hyperion/reference/java/problem-statement.md"))).thenReturn(statement);
        when(resourceLoaderService.getFileResources(any(Path.class))).thenAnswer(invocation -> switch (invocation.<Path>getArgument(0).toString()) {
            case "templates/hyperion/reference/java/template" -> resources("file:/templates/hyperion/reference/java/template/src/example/ScoreCalculator.java", "starter algorithm",
                    "file:/templates/hyperion/reference/java/template/.gitignore", "ignored", "file:/templates/hyperion/reference/java/template/../solution/Evil.java", "escaped");
            case "templates/hyperion/reference/java/solution" ->
                resources("file:/templates/hyperion/reference/java/solution/src/example/ScoreCalculator.java", "solution algorithm");
            case "templates/hyperion/reference/java/tests/test" ->
                resources("file:/templates/hyperion/reference/java/tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java", "behavior tests");
            default -> new Resource[0];
        });
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_MAVEN);
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), resourceLoaderService, tempFileUtilService());

        Map<String, String> reference = service.readReferenceSample(exercise);

        assertThat(reference).containsEntry("reference/problem-statement.md", "Problem statement")
                .containsEntry("reference/template/src/example/ScoreCalculator.java", "starter algorithm")
                .containsEntry("reference/solution/src/example/ScoreCalculator.java", "solution algorithm")
                .containsEntry("reference/tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java", "behavior tests").containsKey("reference/README.md");
        assertThat(reference).doesNotContainKeys("reference/template/.gitignore", "reference/template/../solution/Evil.java", "reference/tests/structural/test.json");
    }

    @Test
    void readReferenceSample_loadsTheDedicatedClasspathExerciseWithoutProjectType() {
        ResourceLoaderService resourceLoaderService = new ResourceLoaderService(new DefaultResourceLoader(), mock());
        ReflectionTestUtils.setField(resourceLoaderService, "templateFileSystemPath", Optional.empty());
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);

        Map<String, String> reference = new GenerationWorkspaceService(mock(), mock(), mock(), resourceLoaderService, tempFileUtilService()).readReferenceSample(exercise);

        assertThat(reference).containsKeys("reference/problem-statement.md", "reference/template/src/de/tum/cit/aet/reference/StandardFeeStrategy.java",
                "reference/template/src/de/tum/cit/aet/reference/ExpressFeeStrategy.java", "reference/solution/src/de/tum/cit/aet/reference/StandardFeeStrategy.java",
                "reference/solution/src/de/tum/cit/aet/reference/ExpressFeeStrategy.java", "reference/solution/src/de/tum/cit/aet/reference/FeeStrategy.java",
                "reference/solution/src/de/tum/cit/aet/reference/ShippingCalculator.java", "reference/tests/test/de/tum/cit/aet/reference/StandardFeeStrategyTest.java",
                "reference/tests/test/de/tum/cit/aet/reference/ExpressFeeStrategyTest.java", "reference/tests/test/de/tum/cit/aet/reference/ShippingCalculatorTest.java");
        // The solution introduces FeeStrategy and ShippingCalculator entirely on its own; the template never sees them (mirrors BubbleSort's Context/Policy/SortStrategy shape),
        // so a multi-class, multi-task design stays available as a worked example instead of only a single-method utility.
        assertThat(reference).doesNotContainKeys("reference/template/src/de/tum/cit/aet/reference/FeeStrategy.java",
                "reference/template/src/de/tum/cit/aet/reference/ShippingCalculator.java", "reference/tests/structural/test.json");
        assertThat(reference.values()).noneMatch(content -> content.contains("${packageName"));
        String statement = reference.get("reference/problem-statement.md");
        assertThat(statement).contains("[task][Implement Standard Fee Strategy](testStandardFeeTypical,testStandardFeeZeroWeight)",
                "[task][Implement Express Fee Strategy](testExpressFeeTypical,testExpressFeeMinimumSurcharge)",
                "[task][Select Strategy By Weight](testSelectsExpressForHeavyPackages,testSelectsStandardForLightPackages)",
                "[task][Compute Total Fee](testComputeFeeDelegatesToChosenStrategy)");
        String calculatorTest = reference.get("reference/tests/test/de/tum/cit/aet/reference/ShippingCalculatorTest.java");
        assertThat(calculatorTest).contains("a 12kg package should select the express strategy", "a 4kg package should select the standard strategy",
                "a 12kg package should be charged the express rate plus surcharge");
    }

    @Test
    void buildReadinessFixture_isVerifierOwnedAndSupportsRegularAndSequentialHarnessLayouts() {
        ResourceLoaderService resourceLoaderService = new ResourceLoaderService(new DefaultResourceLoader(), mock());
        ReflectionTestUtils.setField(resourceLoaderService, "templateFileSystemPath", Optional.empty());
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setBuildConfig(new de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig());
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), resourceLoaderService, tempFileUtilService());

        Map<String, String> regular = service.readBuildReadinessFixture(exercise);
        assertThat(regular).containsKeys("solution/src/de/tum/cit/aet/reference/ScoreCalculator.java", "tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java",
                "tests/test/de/tum/cit/aet/reference/ScoreCalculatorStructureTest.java");
        assertThat(regular.keySet()).noneMatch(path -> path.startsWith("reference/"));

        exercise.getBuildConfig().setSequentialTestRuns(true);
        Map<String, String> sequential = service.readBuildReadinessFixture(exercise);
        assertThat(sequential)
                .containsKeys("tests/structural/test/de/tum/cit/aet/reference/ScoreCalculatorStructureTest.java",
                        "tests/behavior/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java")
                .doesNotContainKeys("tests/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java", "tests/structural/test/de/tum/cit/aet/reference/ScoreCalculatorTest.java");
    }

    @Test
    void readReferenceSample_boundsInputBeforeRejectingAnOversizedResource() throws Exception {
        ResourceLoaderService resourceLoaderService = mock(ResourceLoaderService.class);
        Resource resource = mock(Resource.class);
        CountingInputStream input = new CountingInputStream(new byte[100_000]);
        when(resource.getURI()).thenReturn(java.net.URI.create("file:/templates/hyperion/reference/java/tests/test/Oversized.java"));
        when(resource.getInputStream()).thenReturn(input);
        when(resourceLoaderService.getFileResources(any(Path.class)))
                .thenAnswer(invocation -> invocation.<Path>getArgument(0).endsWith("test") ? new Resource[] { resource } : new Resource[0]);
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_MAVEN);

        new GenerationWorkspaceService(mock(), mock(), mock(), resourceLoaderService, tempFileUtilService()).readReferenceSample(exercise);

        assertThat(input.bytesRead).isLessThanOrEqualTo(64_001);
        assertThat(input.closed).isTrue();
    }

    @Test
    void readReferenceSample_omitsAnIncompleteWorkedExercise() throws Exception {
        ResourceLoaderService resourceLoaderService = mock(ResourceLoaderService.class);
        Resource statement = resource("file:/templates/hyperion/reference/java/problem-statement.md", "Problem statement");
        when(resourceLoaderService.getResource(Path.of("templates/hyperion/reference/java/problem-statement.md"))).thenReturn(statement);
        when(resourceLoaderService.getFileResources(any(Path.class))).thenAnswer(invocation -> switch (invocation.<Path>getArgument(0).toString()) {
            case "templates/hyperion/reference/java/template" -> resources("file:/templates/hyperion/reference/java/template/src/example/ScoreCalculator.java", "starter");
            case "templates/hyperion/reference/java/solution" -> resources("file:/templates/hyperion/reference/java/solution/src/example/ScoreCalculator.java", "solution");
            default -> new Resource[0];
        });
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_MAVEN);

        Map<String, String> reference = new GenerationWorkspaceService(mock(), mock(), mock(), resourceLoaderService, tempFileUtilService()).readReferenceSample(exercise);

        assertThat(reference).isEmpty();
    }

    private static Resource[] resources(String... uriAndContent) throws Exception {
        Resource[] resources = new Resource[uriAndContent.length / 2];
        for (int i = 0; i < uriAndContent.length; i += 2) {
            Resource resource = mock(Resource.class);
            when(resource.getURI()).thenReturn(java.net.URI.create(uriAndContent[i]));
            when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(uriAndContent[i + 1].getBytes(StandardCharsets.UTF_8)));
            resources[i / 2] = resource;
        }
        return resources;
    }

    private static Resource resource(String uri, String content) throws Exception {
        return resources(uri, content)[0];
    }

    @Test
    void seedWorkspace_failsClosedWhenARequiredRepositoryCannotBeCheckedOut() {
        GitService gitService = mock(GitService.class);
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getRepositoryURI(any(RepositoryType.class))).thenReturn(mock(LocalVCRepositoryUri.class));
        GenerationWorkspaceService service = new GenerationWorkspaceService(gitService, mock(), mock(), mock(), tempFileUtilService());

        assertThatThrownBy(() -> service.seedWorkspace(mock(InteractiveSandbox.class), "session", exercise, GenerationMode.GENERATE)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEMPLATE repository");
    }

    @Test
    void extractRepository_rejectsResidueInsteadOfSilentlyPersistingDifferentFiles() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.copyOut("session", "/workspace/solution"))
                .thenReturn(tar(Map.of("solution/src/Main.java", "class Main {}", "solution/template/src/Leak.java", "class Leak {}")));
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        GenerationWorkspaceService.RepositoryExtraction extraction = service.extractRepository(sandbox, "session", RepositoryType.SOLUTION,
                GenerationWorkspaceService.RepositorySeedMetadata.EMPTY);

        assertThat(extraction.extractionFailed()).isTrue();
        assertThat(extraction.files()).containsOnlyKeys("src/Main.java");
    }

    @Test
    void extractProblemStatement_failsClosedWhenTheFileIsMissing() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.copyOut("session", "/workspace/problem-statement.md")).thenReturn(tar(Map.of("other.md", "wrong file")));
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        assertThatThrownBy(() -> service.extractProblemStatement(sandbox, "session")).isInstanceOf(IllegalStateException.class).hasMessageContaining("problem statement");
    }

    @Test
    void extractRepository_rejectsBinaryChangesThatPersistenceCannotRepresent() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.copyOut("session", "/workspace/solution")).thenReturn(tarBytes(Map.of("solution/tool.bin", new byte[] { 0, 4, 5 })));
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        GenerationWorkspaceService.RepositoryExtraction extraction = service.extractRepository(sandbox, "session", RepositoryType.SOLUTION,
                new GenerationWorkspaceService.RepositorySeedMetadata(Map.of("tool.bin", WorkspaceArchive.sha256(new byte[] { 0, 1, 2 })), Set.of()));

        assertThat(extraction.extractionFailed()).isTrue();
    }

    @Test
    void extractRepository_rejectsExecutableModeChanges() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.copyOut("session", "/workspace/solution"))
                .thenReturn(tarBytes(Map.of("solution/run.sh", "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8)), Set.of("solution/run.sh")));
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        GenerationWorkspaceService.RepositoryExtraction extraction = service.extractRepository(sandbox, "session", RepositoryType.SOLUTION,
                GenerationWorkspaceService.RepositorySeedMetadata.EMPTY);

        assertThat(extraction.extractionFailed()).isTrue();
    }

    @Test
    void materializeRepositoryFiles_writesTheCanonicalFilesBackToTheWorkspace() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        Map<String, String> expected = Map.of("solution/gradlew", "#!/bin/sh\n", "solution/src/Main.java", "class Main {}", "tests/test/MainTest.java", "class MainTest {}");
        byte[] binary = { 0, 1, 2, 3 };
        doAnswer(invocation -> {
            try (TarArchiveInputStream tar = new TarArchiveInputStream(invocation.getArgument(2))) {
                WorkspaceArchive.ArchiveContents contents = WorkspaceArchive.readTarContents(tar, "");
                assertThat(contents.textFiles()).containsExactlyInAnyOrderEntriesOf(expected);
                assertThat(contents.binaryDigests()).containsEntry("solution/tool.bin", WorkspaceArchive.sha256(binary));
                assertThat(contents.executableFiles()).containsExactly("solution/gradlew");
            }
            return null;
        }).when(sandbox).copyIn(eq("session"), eq("/workspace"), any());
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        service.materializeRepositoryFiles(sandbox, "session",
                Map.of(RepositoryType.SOLUTION, Map.of("gradlew", "#!/bin/sh\n", "src/Main.java", "class Main {}"), RepositoryType.TESTS,
                        Map.of("test/MainTest.java", "class MainTest {}")),
                Map.of(RepositoryType.SOLUTION, new GenerationWorkspaceService.RepositorySeedMetadata(Map.of("tool.bin", WorkspaceArchive.sha256(binary)), Set.of("gradlew"))),
                Map.of(RepositoryType.SOLUTION, Map.of("tool.bin", new GenerationWorkspaceService.BinarySeedFile(binary))));
    }

    @Test
    void cleanTransientBuildOutputs_removesOnlyKnownBuildDirectoriesFromSeededRepositories() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.exec(eq("session"), any(), eq("sh"), eq("-c"), any())).thenReturn(new SandboxExecResult(0, "", "", false));
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), mock(), mock(), mock(), tempFileUtilService());

        service.cleanTransientBuildOutputs(sandbox, "session");

        verify(sandbox).exec(eq("session"), any(), eq("sh"), eq("-c"), eq("rm -rf -- /workspace/solution/.gradle /workspace/solution/build /workspace/solution/target "
                + "/workspace/solution/buildSrc/.gradle /workspace/solution/buildSrc/build /workspace/template/.gradle /workspace/template/build /workspace/template/target "
                + "/workspace/template/buildSrc/.gradle /workspace/template/buildSrc/build /workspace/tests/.gradle /workspace/tests/build /workspace/tests/target "
                + "/workspace/tests/buildSrc/.gradle /workspace/tests/buildSrc/build"));
    }

    private static TarArchiveInputStream tar(Map<String, String> files) {
        return tarBytes(files.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getBytes(StandardCharsets.UTF_8))));
    }

    private static TempFileUtilService tempFileUtilService() {
        return new TempFileUtilService(Path.of("build/tmp/hyperion-workspace-test"));
    }

    private static TarArchiveInputStream tarBytes(Map<String, byte[]> files) {
        return tarBytes(files, Set.of());
    }

    private static TarArchiveInputStream tarBytes(Map<String, byte[]> files, Set<String> executableFiles) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (TarArchiveOutputStream output = new TarArchiveOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    byte[] content = file.getValue();
                    TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                    entry.setSize(content.length);
                    entry.setMode(executableFiles.contains(file.getKey()) ? 0755 : 0644);
                    output.putArchiveEntry(entry);
                    output.write(content);
                    output.closeArchiveEntry();
                }
            }
            return new TarArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class CountingInputStream extends java.io.InputStream {

        private final byte[] content;

        private int bytesRead;

        private boolean closed;

        private CountingInputStream(byte[] content) {
            this.content = content;
        }

        @Override
        public int read() {
            return bytesRead < content.length ? content[bytesRead++] & 0xff : -1;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (bytesRead >= content.length) {
                return -1;
            }
            int count = Math.min(length, content.length - bytesRead);
            System.arraycopy(content, bytesRead, bytes, offset, count);
            bytesRead += count;
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
