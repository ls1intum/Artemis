package de.tum.cit.aet.artemis.quiz.dto.participation;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionAfterEvaluationDTO;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionBeforeEvaluationDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamQuizParticipationDTO(@JsonUnwrapped StudentQuizParticipationBaseDTO studentQuizParticipationBaseDTO, Set<?> submissions) {

    /**
     * Creates a quiz participation DTO for the student exam response without mutating the underlying participation or quiz submission entities.
     *
     * @param studentParticipation the student participation to map
     * @param includeSolutions     whether quiz solution fields should be included in submitted answers
     * @return the response DTO
     */
    public static StudentExamQuizParticipationDTO of(StudentParticipation studentParticipation, boolean includeSolutions) {
        return new StudentExamQuizParticipationDTO(StudentQuizParticipationBaseDTO.of(studentParticipation),
                mapQuizSubmissions(studentParticipation.getSubmissions(), includeSolutions));
    }

    private static Set<?> mapQuizSubmissions(Set<Submission> submissions, boolean includeSolutions) {
        if (submissions == null || !Hibernate.isInitialized(submissions)) {
            return Set.of();
        }
        return submissions.stream().filter(QuizSubmission.class::isInstance).map(QuizSubmission.class::cast)
                .map(submission -> includeSolutions ? QuizSubmissionAfterEvaluationDTO.of(submission) : QuizSubmissionBeforeEvaluationDTO.of(submission))
                .collect(Collectors.toSet());
    }
}
