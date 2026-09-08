package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TreeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationRequestService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class GenerationSeedServiceTest {

    @TempDir
    Path temporary;

    private final GitService git = mock();

    private final GenerationRequestService requests = mock();

    private final ProgrammingExercise exercise = mock();

    private final LocalVCRepositoryUri uri = new LocalVCRepositoryUri(URI.create("https://artemis.example"), "TEST", "TEST-template");

    private final List<String> commits = new ArrayList<>();

    private GenerationSeedService service;

    private FileMode sourceMode = FileMode.REGULAR_FILE;

    private byte[] sourceBytes = "class App {}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setup() throws Exception {
        service = new GenerationSeedService(git, new TempFileUtilService(temporary), requests, "main");
        when(exercise.getRepositoryURI(any())).thenReturn(uri);
        when(exercise.getProblemStatement()).thenReturn("The instructor's specification.");
        when(requests.isAuthoritativeProblemStatement(exercise)).thenReturn(true);
        when(git.getOrCheckoutRepository(eq(uri), eq(uri), any(Path.class), eq(true), eq("main"), eq(false))).thenAnswer(invocation -> createRepository(invocation.getArgument(2)));
    }

    @Test
    void capturesCommittedBytesModesAndExactHeadsAndDeletesTemporaryTrees() {
        var seed = service.capture(exercise);
        assertThat(seed.heads()).containsKeys(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);
        assertThat(seed.heads().values()).containsAll(commits);
        assertThat(seed.snapshot().files()).hasSize(10);
        for (String role : List.of("template", "solution", "tests")) {
            assertThat(seed.snapshot().files()).filteredOn(file -> file.path().equals(role + "/gradlew")).singleElement().satisfies(file -> {
                assertThat(file.executable()).isTrue();
                assertThat(file.content()).isEqualTo("#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
            });
            assertThat(seed.snapshot().files()).filteredOn(file -> file.path().equals(role + "/wrapper.jar")).singleElement()
                    .satisfies(file -> assertThat(file.content()).containsExactly(0, 1, 2));
        }
        assertThat(seed.snapshot().files()).filteredOn(file -> file.path().equals("problem-statement.md")).singleElement()
                .satisfies(file -> assertThat(new String(file.content(), StandardCharsets.UTF_8)).isEqualTo("The instructor's specification."));
        assertThat(temporary.toFile().list()).isEmpty();
    }

    @Test
    void seededSampleStatementIsNotSentAsAnAuthoritativeContract() {
        when(requests.isAuthoritativeProblemStatement(exercise)).thenReturn(false);
        assertThat(service.capture(exercise).snapshot().files()).filteredOn(file -> file.path().equals("problem-statement.md")).singleElement()
                .satisfies(file -> assertThat(file.content()).isEmpty());
        assertThat(temporary.toFile().list()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("unsupportedModes")
    void linksAndSubmodulesAreRejectedBeforeWorkerTransport(FileMode mode) {
        sourceMode = mode;
        assertThatThrownBy(() -> service.capture(exercise)).isInstanceOf(IllegalStateException.class).hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining("links or submodules");
        assertThat(temporary.toFile().list()).isEmpty();
    }

    @Test
    void oversizedBlobIsRejectedAndCheckoutIsRemoved() {
        sourceBytes = new byte[WorkspaceFile.MAX_FILE_BYTES + 1];
        assertThatThrownBy(() -> service.capture(exercise)).isInstanceOf(IllegalStateException.class);
        assertThat(temporary.toFile().list()).isEmpty();
    }

    @Test
    void missingRepositoryStillCleansItsTemporaryDirectory() {
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(null);
        assertThatThrownBy(() -> service.capture(exercise)).isInstanceOf(IllegalStateException.class).hasStackTraceContaining("Missing generation repository");
        assertThat(temporary.toFile().list()).isEmpty();
    }

    private Repository createRepository(Path path) throws Exception {
        Repository repository = new Repository(path.resolve(".git").toString(), uri);
        repository.create();
        try (var objects = repository.newObjectInserter()) {
            TreeFormatter tree = new TreeFormatter();
            tree.append("App.java", sourceMode, objects.insert(Constants.OBJ_BLOB, sourceBytes));
            tree.append("gradlew", FileMode.EXECUTABLE_FILE, objects.insert(Constants.OBJ_BLOB, "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8)));
            tree.append("wrapper.jar", FileMode.REGULAR_FILE, objects.insert(Constants.OBJ_BLOB, new byte[] { 0, 1, 2 }));
            CommitBuilder commit = new CommitBuilder();
            commit.setTreeId(objects.insert(tree));
            commit.setAuthor(new PersonIdent("Instructor", "instructor@example.org"));
            commit.setCommitter(commit.getAuthor());
            commit.setMessage("Seed exercise");
            var id = objects.insert(commit);
            objects.flush();
            var head = repository.updateRef(Constants.HEAD);
            head.setNewObjectId(id);
            head.update();
            commits.add(id.name());
        }
        return repository;
    }

    private static Stream<FileMode> unsupportedModes() {
        return Stream.of(FileMode.SYMLINK, FileMode.GITLINK);
    }
}
