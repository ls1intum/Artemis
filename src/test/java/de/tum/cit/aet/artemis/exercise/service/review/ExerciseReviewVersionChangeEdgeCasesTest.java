package de.tum.cit.aet.artemis.exercise.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.InlineCodeChangeDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.exception.GitException;

/** Exercises conservative anchor handling when repository or comment metadata is incomplete. */
class ExerciseReviewVersionChangeEdgeCasesTest {

    @TempDir
    private Path directory;

    private final CommentThreadRepository threads = mock(CommentThreadRepository.class);

    private final CommentRepository comments = mock(CommentRepository.class);

    private final BareGitRepositoryService repositories = mock(BareGitRepositoryService.class);

    private final ExerciseReviewVersionChangeService service = new ExerciseReviewVersionChangeService(threads, comments, repositories);

    @ParameterizedTest
    @MethodSource("textMappings")
    void mapsTextInsertionsDeletionsAndNullSnapshots(String oldText, String newText, int oldLine, Integer expectedLine, boolean outdated) {
        var mapped = service.mapLineInText(oldText, newText, oldLine);
        assertThat(mapped.newLine()).isEqualTo(expectedLine);
        assertThat(mapped.outdated()).isEqualTo(outdated);
        verifyNoInteractions(threads, comments, repositories);
    }

    private static Stream<Arguments> textMappings() {
        return Stream.of(Arguments.of(null, null, 1, 1, false), Arguments.of(null, "new\n", 1, 2, false), Arguments.of("old\n", null, 1, 1, true),
                Arguments.of("a\nb\nc\n", "a\nc\n", 2, 2, true), Arguments.of("a\nb\nc\n", "a\nc\n", 3, 2, false), Arguments.of("a\nb\nc\n", "a\nB\nC\nD\n", 3, 3, true),
                Arguments.of("a\nb\n", "x\na\nb\n", 1, 2, false), Arguments.of("a\nb\n", "a\nx\nb\n", 1, 1, false), Arguments.of("a", "b", 0, null, true),
                Arguments.of("a", "b", -1, null, true));
    }

    @ParameterizedTest
    @CsvSource({ "0", "-1" })
    void rejectsInvalidRepositoryAnchorWithoutOpeningGit(int line) {
        var result = service.mapLine(new LocalVCRepositoryUri("https://example.org/git/COURSE/course-tests.git"), "main.txt", "old", "new", line);
        assertThat(result.newLine()).isNull();
        assertThat(result.outdated()).isTrue();
        verifyNoInteractions(repositories);
    }

    @Test
    void wrapsRepositoryReadFailureWithFileContext() throws IOException {
        var uri = new LocalVCRepositoryUri("https://example.org/git/COURSE/course-tests.git");
        var failure = new IOException("repository unavailable");
        var repository = spy(new Repository(directory.resolve("repository.git").toString(), uri));
        doThrow(failure).when(repository).resolve(anyString());
        when(repositories.getBareRepository(uri, false)).thenReturn(repository);

        assertThatThrownBy(() -> service.mapLine(uri, "main.txt", "old", "new", 1)).isInstanceOf(GitException.class).hasMessage("Cannot map line for file main.txt")
                .hasCause(failure);
    }

    @ParameterizedTest
    @MethodSource("unmappableThreads")
    void ignoresIncompleteThreadMetadataWithoutChangingOrSavingTheThread(CommentThread thread) {
        when(threads.findByExerciseIdAndOutdatedFalseAndLineNumberIsNotNull(1L)).thenReturn(List.of(thread));

        var result = service.updateThreadsForVersionChange(snapshot("old", null), snapshot("new", null));

        assertThat(result).isEmpty();
        assertThat(thread.isOutdated()).isFalse();
        verify(threads, never()).saveAll(any());
        verifyNoInteractions(comments, repositories);
    }

    private static Stream<CommentThread> unmappableThreads() {
        var noId = thread(CommentThreadLocationType.PROBLEM_STATEMENT, 2);
        noId.setId(null);
        var noLine = thread(CommentThreadLocationType.PROBLEM_STATEMENT, 2);
        noLine.setLineNumber(null);
        return Stream.of(noId, noLine, thread(CommentThreadLocationType.TEMPLATE_REPO, 2));
    }

    @ParameterizedTest
    @MethodSource("incompleteRepositorySnapshots")
    void skipsRepositoryThreadsWhenNoComparableCommitPairExists(CommentThreadLocationType target, ProgrammingExerciseSnapshotDTO previous, ProgrammingExerciseSnapshotDTO current,
            String path, Long auxiliaryId) {
        var thread = thread(target, 3);
        thread.setFilePath(path);
        thread.setAuxiliaryRepositoryId(auxiliaryId);
        when(threads.findByExerciseIdAndOutdatedFalseAndLineNumberIsNotNull(1L)).thenReturn(List.of(thread));

        assertThat(service.updateThreadsForVersionChange(snapshot(null, previous), snapshot(null, current))).isEmpty();

        assertThat(thread.getLineNumber()).isEqualTo(3);
        assertThat(thread.isOutdated()).isFalse();
        verify(threads, never()).saveAll(any());
        verifyNoInteractions(comments, repositories);
    }

    private static Stream<Arguments> incompleteRepositorySnapshots() {
        var empty = programming(null, null, null, null, null);
        var complete = participation("commit", "https://example.org/git/COURSE/course-tests.git");
        var missingCommit = participation(null, "https://example.org/git/COURSE/course-tests.git");
        var missingUri = participation("next", null);
        var same = programming(null, null, complete, complete, null);
        return Stream.of(Arguments.of(CommentThreadLocationType.TEMPLATE_REPO, empty, null, "file", null),
                Arguments.of(CommentThreadLocationType.TEMPLATE_REPO, empty, empty, null, null), Arguments.of(CommentThreadLocationType.TEMPLATE_REPO, empty, empty, " ", null),
                Arguments.of(CommentThreadLocationType.TEMPLATE_REPO, empty, empty, "file", null),
                Arguments.of(CommentThreadLocationType.TEMPLATE_REPO, same, programming(null, null, missingCommit, null, null), "file", null),
                Arguments.of(CommentThreadLocationType.TEMPLATE_REPO, programming(null, null, missingCommit, null, null), same, "file", null),
                Arguments.of(CommentThreadLocationType.TEMPLATE_REPO, same, programming(null, null, missingUri, null, null), "file", null),
                Arguments.of(CommentThreadLocationType.TEMPLATE_REPO, same, same, "file", null), Arguments.of(CommentThreadLocationType.SOLUTION_REPO, same, same, "file", null),
                Arguments.of(CommentThreadLocationType.TEST_REPO, empty, empty, "file", null),
                Arguments.of(CommentThreadLocationType.TEST_REPO, programming("uri", "old", null, null, null), empty, "file", null),
                Arguments.of(CommentThreadLocationType.TEST_REPO, programming("uri", "old", null, null, null), programming(null, "new", null, null, null), "file", null),
                Arguments.of(CommentThreadLocationType.AUXILIARY_REPO, empty, empty, "file", null),
                Arguments.of(CommentThreadLocationType.AUXILIARY_REPO, empty, empty, "file", 2L),
                Arguments.of(CommentThreadLocationType.AUXILIARY_REPO, programming(null, null, null, null, List.of()), empty, "file", 2L),
                Arguments.of(CommentThreadLocationType.AUXILIARY_REPO, programming(null, null, null, null, List.of()), programming(null, null, null, null, List.of()), "file", 2L));
    }

    @ParameterizedTest
    @CsvSource({ "1, 3, 1, 4, true", "2, 3, 3, 4, false", "3, 3, 4, 4, false", "1, 1, 1, 1, false", "0, 2, 0, 2, true", "3, 2, 3, 3, true" })
    void mapsInlineFixRangesAndMarksInteriorInsertionsOutdated(int start, int end, int expectedStart, int expectedEnd, boolean outdated) {
        var thread = thread(CommentThreadLocationType.PROBLEM_STATEMENT, 1);
        var fix = new InlineCodeChangeDTO(start, end, "expected", "replacement", false);
        var content = new ConsistencyIssueCommentContentDTO(null, null, "finding", fix);
        var comment = new Comment();
        comment.setId(2L);
        comment.setThread(thread);
        comment.setContent(content);
        when(threads.findByExerciseIdAndOutdatedFalseAndLineNumberIsNotNull(1L)).thenReturn(List.of(thread));
        when(comments.findByThreadIdsAndType(Set.of(1L), CommentType.CONSISTENCY_CHECK)).thenReturn(List.of(comment));

        var modified = service.updateThreadsForVersionChange(snapshot("a\nb\nc\n", null), snapshot("a\ninserted\nb\nc\n", null));

        var actual = (ConsistencyIssueCommentContentDTO) comment.getContent();
        assertThat(actual.text()).isEqualTo("finding");
        assertThat(actual.suggestedFix()).isEqualTo(new InlineCodeChangeDTO(expectedStart, expectedEnd, "expected", "replacement", false));
        assertThat(thread.isOutdated()).isEqualTo(outdated);
        assertThat(thread.getLineNumber()).isOne();
        assertThat(modified).containsExactlyElementsOf(outdated ? List.of(thread) : List.of());
        if (start != expectedStart || end != expectedEnd) {
            verify(comments).saveAll(Set.of(comment));
        }
        else {
            verify(comments, never()).saveAll(any());
        }
    }

    @Test
    void ignoresOrphanedAndUnrelatedCommentsAndCommentsWithoutSuggestedFixes() {
        var thread = thread(CommentThreadLocationType.PROBLEM_STATEMENT, 1);
        var orphan = new Comment();
        var unpersisted = new Comment();
        unpersisted.setThread(new CommentThread());
        var unrelated = new Comment();
        var otherThread = thread(CommentThreadLocationType.PROBLEM_STATEMENT, 1);
        otherThread.setId(99L);
        unrelated.setThread(otherThread);
        var user = new Comment();
        user.setThread(thread);
        user.setContent(new UserCommentContentDTO("ordinary comment"));
        var noFix = new Comment();
        noFix.setThread(thread);
        noFix.setContent(new ConsistencyIssueCommentContentDTO(null, null, "no fix", null));
        when(threads.findByExerciseIdAndOutdatedFalseAndLineNumberIsNotNull(1L)).thenReturn(List.of(thread));
        when(comments.findByThreadIdsAndType(Set.of(1L), CommentType.CONSISTENCY_CHECK)).thenReturn(List.of(orphan, unpersisted, unrelated, user, noFix));

        assertThat(service.updateThreadsForVersionChange(snapshot("a", null), snapshot("a", null))).isEmpty();

        assertThat(user.getContent()).isEqualTo(new UserCommentContentDTO("ordinary comment"));
        assertThat(thread.getLineNumber()).isOne();
        assertThat(thread.isOutdated()).isFalse();
        verify(comments, never()).saveAll(any());
        verify(threads, never()).saveAll(any());
    }

    @Test
    void failedRepositoryGroupMarksItsThreadOutdatedAndStillMapsProblemStatementThreads() throws IOException {
        var repositoryThread = thread(CommentThreadLocationType.TEMPLATE_REPO, 2);
        repositoryThread.setFilePath("file");
        var textThread = thread(CommentThreadLocationType.PROBLEM_STATEMENT, 2);
        textThread.setId(2L);
        var oldProgramming = programming(null, null, participation("old", "https://example.org/git/COURSE/course-tests.git"), null, null);
        var newProgramming = programming(null, null, participation("new", "https://example.org/git/COURSE/course-tests.git"), null, null);
        when(threads.findByExerciseIdAndOutdatedFalseAndLineNumberIsNotNull(1L)).thenReturn(List.of(repositoryThread, textThread));
        when(repositories.getBareRepository(any(), eq(false))).thenThrow(new GitException("unavailable"));

        var changed = service.updateThreadsForVersionChange(snapshot("a\nb\n", oldProgramming), snapshot("inserted\na\nb\n", newProgramming));

        assertThat(changed).containsExactlyInAnyOrder(repositoryThread, textThread);
        assertThat(repositoryThread.isOutdated()).isTrue();
        assertThat(repositoryThread.getLineNumber()).isEqualTo(2);
        assertThat(textThread.isOutdated()).isFalse();
        assertThat(textThread.getLineNumber()).isEqualTo(3);
        verify(threads).saveAll(Set.of(repositoryThread, textThread));
    }

    private static CommentThread thread(CommentThreadLocationType type, int line) {
        var thread = new CommentThread();
        thread.setId(1L);
        thread.setTargetType(type);
        thread.setLineNumber(line);
        return thread;
    }

    private static ExerciseSnapshotDTO snapshot(String text, ProgrammingExerciseSnapshotDTO programming) {
        return new ExerciseSnapshotDTO(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, text, null, null, null, null, null, null,
                null, programming, null, null, null, null, null);
    }

    private static ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO participation(String commit, String uri) {
        return new ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO(1L, uri, null, commit);
    }

    private static ProgrammingExerciseSnapshotDTO programming(String uri, String commit, ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO template,
            ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO solution, List<ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO> auxiliary) {
        return new ProgrammingExerciseSnapshotDTO(uri, auxiliary, null, null, null, null, null, null, null, null, null, null, template, solution, null, null, null, null, null,
                null, null, commit);
    }
}
