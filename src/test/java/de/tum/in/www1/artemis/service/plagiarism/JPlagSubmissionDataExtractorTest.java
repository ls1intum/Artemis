package de.tum.in.www1.artemis.service.plagiarism;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

class JPlagSubmissionDataExtractorTest {

    private final SubmissionRepository submissionRepository = mock();

    private final JPlagSubmissionDataExtractor extractor = new JPlagSubmissionDataExtractor(submissionRepository);

    @Test
    void shouldFailForQuizExercise() {
        // given
        var plagiarismSubmission = new PlagiarismSubmission<TextSubmissionElement>();
        var jPlagSubmission = new de.jplag.Submission("name", null, true, emptyList(), null);
        var quizExercise = new QuizExercise();

        // expect
        assertThatThrownBy(() -> extractor.getAndSetSubmissionIdAndStudentLogin(plagiarismSubmission, jPlagSubmission, quizExercise)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(submissionRepository);
    }

    @Test
    void shouldFailForModellingExercise() {
        // given
        var plagiarismSubmission = new PlagiarismSubmission<TextSubmissionElement>();
        var jPlagSubmission = new de.jplag.Submission("name", null, true, emptyList(), null);
        var modelingExercise = new ModelingExercise();

        // expect
        assertThatThrownBy(() -> extractor.getAndSetSubmissionIdAndStudentLogin(plagiarismSubmission, jPlagSubmission, modelingExercise)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(submissionRepository);
    }

    @Test
    void shouldExtractDataCorrectlyForTextExerciseSubmission() {
        // given
        var submissionId = "123";
        var studentLogin = "login321";

        // and
        var plagiarismSubmission = new PlagiarismSubmission<TextSubmissionElement>();
        var jPlagSubmission = new de.jplag.Submission(format("%s-%s", submissionId, studentLogin), null, true, emptyList(), null);
        var textExercise = new TextExercise();

        // when
        extractor.getAndSetSubmissionIdAndStudentLogin(plagiarismSubmission, jPlagSubmission, textExercise);

        // then
        verifyNoInteractions(submissionRepository);
        assertThat(plagiarismSubmission).extracting(PlagiarismSubmission::getSubmissionId).isEqualTo(123L);
        assertThat(plagiarismSubmission).extracting(PlagiarismSubmission::getStudentLogin).isEqualTo(studentLogin);
    }

    @Test
    void shouldExtractDataCorrectlyForProgramingExerciseSubmission() {
        // given: participation id in jplag submission name
        var participationId = "456";
        var studentLogin = "login321";
        var submission = new ProgrammingSubmission();

        // and: submission id stored in the db
        submission.setId(123L);
        when(submissionRepository.findAllByParticipationId(456)).thenReturn(List.of(submission));

        // and
        var plagiarismSubmission = new PlagiarismSubmission<TextSubmissionElement>();
        var jPlagSubmission = new de.jplag.Submission(format("%s-%s", participationId, studentLogin), null, true, emptyList(), null);
        var programmingExercise = new ProgrammingExercise();

        // when
        extractor.getAndSetSubmissionIdAndStudentLogin(plagiarismSubmission, jPlagSubmission, programmingExercise);

        // then
        assertThat(plagiarismSubmission).extracting(PlagiarismSubmission::getSubmissionId).isEqualTo(123L);
        assertThat(plagiarismSubmission).extracting(PlagiarismSubmission::getStudentLogin).isEqualTo(studentLogin);
    }

    @Test
    void shouldExtractDataCorrectlyForLegacyProgramingExerciseSubmission() {
        // given
        var submissionId = "123";
        var participationId = "456";
        var studentLogin = "login321";

        // and
        var plagiarismSubmission = new PlagiarismSubmission<TextSubmissionElement>();
        var jPlagSubmission = new de.jplag.Submission(format("%s-%s-%s", participationId, submissionId, studentLogin), null, true, emptyList(), null);
        var programmingExercise = new ProgrammingExercise();

        // when
        extractor.getAndSetSubmissionIdAndStudentLogin(plagiarismSubmission, jPlagSubmission, programmingExercise);

        // then
        verifyNoInteractions(submissionRepository);
        assertThat(plagiarismSubmission).extracting(PlagiarismSubmission::getSubmissionId).isEqualTo(123L);
        assertThat(plagiarismSubmission).extracting(PlagiarismSubmission::getStudentLogin).isEqualTo(studentLogin);
    }
}
