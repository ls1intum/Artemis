package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseOverviewDTO(Long id, PlagiarismCaseExerciseDTO exercise, PlagiarismCaseUserDTO student, PlagiarismCasePostSummaryDTO post, PlagiarismVerdict verdict,
        ZonedDateTime verdictDate, PlagiarismCaseUserDTO verdictBy, int plagiarismSubmissionCount, boolean createdByContinuousPlagiarismControl, boolean hasStudentAnswer) {

    /**
     * JPQL constructor for overview projections. It keeps optional nested DTOs absent when the joined entity is absent.
     *
     * @param id                                   the plagiarism case id
     * @param exercise                             the exercise DTO
     * @param studentId                            the affected student id
     * @param studentLogin                         the affected student login
     * @param studentName                          the affected student name
     * @param studentVisibleRegistrationNumber     the affected student's visible registration number
     * @param postId                               the post id
     * @param postCreationDate                     the post creation date
     * @param hasStudentAnswer                     whether the affected student answered the notification post
     * @param verdict                              the plagiarism verdict
     * @param verdictDate                          the verdict date
     * @param verdictById                          the verdict author id
     * @param verdictByLogin                       the verdict author login
     * @param verdictByName                        the verdict author name
     * @param verdictByVisibleRegistrationNumber   the verdict author's visible registration number
     * @param plagiarismSubmissionCount            the number of submissions attached to the plagiarism case
     * @param createdByContinuousPlagiarismControl whether the case was created by continuous plagiarism control
     */
    public PlagiarismCaseOverviewDTO(Long id, PlagiarismCaseExerciseDTO exercise, Long studentId, String studentLogin, String studentName, String studentVisibleRegistrationNumber,
            Long postId, ZonedDateTime postCreationDate, boolean hasStudentAnswer, PlagiarismVerdict verdict, ZonedDateTime verdictDate, Long verdictById, String verdictByLogin,
            String verdictByName, String verdictByVisibleRegistrationNumber, long plagiarismSubmissionCount, boolean createdByContinuousPlagiarismControl) {
        this(id, exercise, userOrNull(studentId, studentLogin, studentName, studentVisibleRegistrationNumber), postOrNull(postId, postCreationDate), verdict, verdictDate,
                userOrNull(verdictById, verdictByLogin, verdictByName, verdictByVisibleRegistrationNumber), Math.toIntExact(plagiarismSubmissionCount),
                createdByContinuousPlagiarismControl, hasStudentAnswer);
    }

    /**
     * Maps a plagiarism case entity to the overview DTO.
     *
     * @param plagiarismCase the plagiarism case entity
     * @return the DTO representation
     */
    public static PlagiarismCaseOverviewDTO ofOverview(PlagiarismCase plagiarismCase) {
        if (plagiarismCase == null) {
            return null;
        }

        int plagiarismSubmissionCount = 0;
        if (plagiarismCase.getPlagiarismSubmissions() != null && Hibernate.isInitialized(plagiarismCase.getPlagiarismSubmissions())) {
            plagiarismSubmissionCount = plagiarismCase.getPlagiarismSubmissions().size();
        }

        return new PlagiarismCaseOverviewDTO(plagiarismCase.getId(), PlagiarismCaseExerciseDTO.fromExercise(plagiarismCase.getExercise()),
                PlagiarismCaseUserDTO.fromUser(plagiarismCase.getStudent()), PlagiarismCasePostSummaryDTO.fromPost(plagiarismCase.getPost()), plagiarismCase.getVerdict(),
                plagiarismCase.getVerdictDate(), PlagiarismCaseUserDTO.fromUser(plagiarismCase.getVerdictBy()), plagiarismSubmissionCount,
                plagiarismCase.isCreatedByContinuousPlagiarismControl(), false);
    }

    private static PlagiarismCaseUserDTO userOrNull(Long id, String login, String name, String visibleRegistrationNumber) {
        if (id == null) {
            return null;
        }
        return new PlagiarismCaseUserDTO(id, login, name, visibleRegistrationNumber);
    }

    private static PlagiarismCasePostSummaryDTO postOrNull(Long id, ZonedDateTime creationDate) {
        if (id == null) {
            return null;
        }
        return new PlagiarismCasePostSummaryDTO(id, creationDate);
    }
}
