package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Unit tests for the submitted-vs-local diff set. Most of them drive {@link PyrisDTOService#buildSubmittedRepository}
 * directly: the result is a pure function of committed + uncommitted files + language, so the committed side can be
 * controlled without a repository. The last one goes through both {@code toPyrisSubmissionDTO} overloads with only
 * the repository fetch mocked, which is the single piece of I/O in that path. No Spring context and no database.
 */
class PyrisSubmittedRepositoryTest {

    private static final ProgrammingLanguage JAVA = ProgrammingLanguage.JAVA;

    @Test
    void changedExistingCodeFile_carriesCommittedContent() {
        var committed = Map.of("src/Main.java", "OLD");
        var uncommitted = Map.of("src/Main.java", "NEW");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).containsExactly(entry("src/Main.java", "OLD"));
    }

    @Test
    void newLocalCodeFile_carriesEmptySubmittedSide_whenCommittedSetWasRead() {
        var committed = Map.of("src/Existing.java", "x");
        var uncommitted = Map.of("src/New.java", "brand new");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).containsExactly(entry("src/New.java", ""));
    }

    @Test
    void readableButEmptyCommittedSet_stillMarksLocalFilesAsNew() {
        // A repository that checks out fine but holds no file of the exercise language filters down to an empty map.
        // That is a fact about its contents, not about the fetch, so the student's local files are genuinely new and
        // must keep their all-added baseline. Deriving readability from the map being empty lost exactly this case.
        var committed = Map.<String, String>of();
        var uncommitted = Map.of("src/Main.java", "code");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).containsExactly(entry("src/Main.java", ""));
    }

    @Test
    void unchangedCodeFile_isSkipped() {
        var committed = Map.of("src/Main.java", "same");
        var uncommitted = Map.of("src/Main.java", "same");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).isEmpty();
    }

    @Test
    void nonLanguageFile_isExcluded() {
        var committed = Map.of("src/Main.java", "x");
        var uncommitted = Map.of("README.md", "# changed");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).isEmpty();
    }

    @Test
    void unreadableCommittedSet_doesNotFabricateAllAddedDiff() {
        // Repo fetch failed / no commits yet. Do NOT claim every changed file is "new".
        var committed = Map.<String, String>of();
        var uncommitted = Map.of("src/Main.java", "code");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, false)).isEmpty();
    }

    @Test
    void nullLanguage_treatsAllFilesAsCode() {
        // language == null => no extension filtering, mirroring getFilteredRepositoryContents.
        var committed = Map.of("README.md", "OLD");
        var uncommitted = Map.of("README.md", "NEW");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, null, true)).containsExactly(entry("README.md", "OLD"));
    }

    @Test
    void bothSubmissionOverloads_reportAnEmptyDiff_whenTheStudentHasNoLocalEdits() throws IOException {
        // The no-argument overload has to hand an empty uncommitted map to the two-argument one; if it ever passed
        // the committed contents instead, every file would look changed. Only the repository fetch is mocked, so the
        // merge and the diff below it are the production ones.
        var repositoryService = mock(RepositoryService.class);
        when(repositoryService.getContextFilesContentFromBareRepositoryForLastCommit(any(LocalVCRepositoryUri.class), any(), anyLong()))
                .thenReturn(Map.of("src/Main.java", "COMMITTED"));
        var service = new PyrisDTOService(repositoryService, mock(ProgrammingFeedbackSynthesizerService.class), mock(BuildLogEntryService.class),
                mock(IrisProactiveProperties.class));

        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(JAVA);
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setRepositoryUri("http://localhost:8080/git/ABC/abc-student1.git");
        var submission = new ProgrammingSubmission();
        submission.setId(1L);
        submission.setParticipation(participation);

        assertThat(service.toPyrisSubmissionDTO(submission).submittedRepository()).isEmpty();
        assertThat(service.toPyrisSubmissionDTO(submission, Map.of()).submittedRepository()).isEmpty();
        // The committed side really was read, so the empty diff means "nothing changed", not "nothing fetched".
        assertThat(service.toPyrisSubmissionDTO(submission).repository()).containsExactly(entry("src/Main.java", "COMMITTED"));
    }

    @Test
    void testsRepository_isFilteredByLanguageLikeTheOtherRepositories() throws IOException {
        // A tests repository can hold large data sets next to the tests. Only the files of the exercise language are context for Iris.
        var repositoryService = mock(RepositoryService.class);
        when(repositoryService.getContextFilesContentFromBareRepositoryForLastCommit(any(LocalVCRepositoryUri.class), any(), anyLong()))
                .thenReturn(Map.of("src/test/MainTest.java", "TEST", "data/scenario.xml", "HUGE"));
        var service = new PyrisDTOService(repositoryService, mock(ProgrammingFeedbackSynthesizerService.class), mock(BuildLogEntryService.class),
                mock(IrisProactiveProperties.class));
        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(JAVA);
        exercise.setId(1L);
        exercise.setTestRepositoryUri("http://localhost:8080/git/ABC/abc-tests.git");

        assertThat(service.toPyrisProgrammingExerciseDTO(exercise).testRepository()).containsExactly(entry("src/test/MainTest.java", "TEST"));
    }

    @Test
    void aChangedFileThatTheBoundedReadLeftOutKeepsItsCommittedBaseline() throws IOException {
        // The bounded read leaves out a committed file above the size limit. The edited file must still be diffed against its committed content, not sent as new.
        var repositoryService = mock(RepositoryService.class);
        when(repositoryService.getContextFilesContentFromBareRepositoryForLastCommit(any(LocalVCRepositoryUri.class), any(), anyLong()))
                .thenReturn(Map.of("src/Main.java", "COMMITTED"));
        when(repositoryService.getCommittedContentOfPaths(any(LocalVCRepositoryUri.class), eq(Set.of("src/Huge.java", "src/New.java"))))
                .thenReturn(new RepositoryService.CommittedPathContents(Map.of("src/Huge.java", "COMMITTED HUGE"), Set.of()));
        var service = new PyrisDTOService(repositoryService, mock(ProgrammingFeedbackSynthesizerService.class), mock(BuildLogEntryService.class),
                mock(IrisProactiveProperties.class));
        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(JAVA);
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setRepositoryUri("http://localhost:8080/git/ABC/abc-student1.git");
        var submission = new ProgrammingSubmission();
        submission.setId(1L);
        submission.setParticipation(participation);

        var dto = service.toPyrisSubmissionDTO(submission, Map.of("src/Huge.java", "EDITED HUGE", "src/New.java", "NEW"));

        assertThat(dto.submittedRepository()).containsOnly(entry("src/Huge.java", "COMMITTED HUGE"), entry("src/New.java", ""));
    }

    @Test
    void aBaselineThatDoesNotFitTheLimitsIsNotSentAndMakesTheDiffIncomplete() throws IOException {
        // A large committed file that the student replaces by a small local edit must not bring its content back into the request
        var repositoryService = mock(RepositoryService.class);
        when(repositoryService.getContextFilesContentFromBareRepositoryForLastCommit(any(LocalVCRepositoryUri.class), any(), anyLong()))
                .thenReturn(Map.of("src/Main.java", "COMMITTED"));
        when(repositoryService.getCommittedContentOfPaths(any(LocalVCRepositoryUri.class), eq(Set.of("src/Huge.java", "src/New.java"))))
                .thenReturn(new RepositoryService.CommittedPathContents(Map.of(), Set.of("src/Huge.java")));
        var service = new PyrisDTOService(repositoryService, mock(ProgrammingFeedbackSynthesizerService.class), mock(BuildLogEntryService.class),
                mock(IrisProactiveProperties.class));
        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(JAVA);
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setRepositoryUri("http://localhost:8080/git/ABC/abc-student1.git");
        var submission = new ProgrammingSubmission();
        submission.setId(1L);
        submission.setParticipation(participation);

        var dto = service.toPyrisSubmissionDTO(submission, Map.of("src/Huge.java", "SMALL EDIT", "src/New.java", "NEW"));

        // The file is neither sent with its committed content nor as a new file, and the diff is marked incomplete
        assertThat(dto.submittedRepository()).containsOnly(entry("src/New.java", ""));
        assertThat(dto.submittedRepositoryAvailable()).isFalse();
    }
}
