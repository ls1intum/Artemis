package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseOverviewDTO(Long id, PlagiarismCaseExerciseDTO exercise, @Nullable PlagiarismCaseUserDTO student, @Nullable PlagiarismCasePostSummaryDTO post,
        @Nullable PlagiarismVerdict verdict, @Nullable ZonedDateTime verdictDate, @Nullable PlagiarismCaseUserDTO verdictBy, int plagiarismSubmissionCount,
        boolean createdByContinuousPlagiarismControl, boolean hasStudentAnswer) {

    /**
     * JPQL constructor for overview projections. It keeps optional nested DTOs absent when the joined entity is absent.
     *
     * @param id                                   the plagiarism case id
     * @param exercise                             the exercise DTO
     * @param studentId                            the affected student id
     * @param studentLogin                         the affected student login
     * @param studentFirstName                     the affected student's first name
     * @param studentLastName                      the affected student's last name
     * @param postId                               the post id
     * @param postCreationDate                     the post creation date
     * @param hasStudentAnswer                     whether the affected student answered the notification post
     * @param verdict                              the plagiarism verdict
     * @param verdictDate                          the verdict date
     * @param verdictById                          the verdict author id
     * @param verdictByLogin                       the verdict author login
     * @param verdictByFirstName                   the verdict author's first name
     * @param verdictByLastName                    the verdict author's last name
     * @param plagiarismSubmissionCount            the number of submissions attached to the plagiarism case
     * @param createdByContinuousPlagiarismControl whether the case was created by continuous plagiarism control
     */
    public PlagiarismCaseOverviewDTO(Long id, PlagiarismCaseExerciseDTO exercise, @Nullable Long studentId, @Nullable String studentLogin, @Nullable String studentFirstName,
            @Nullable String studentLastName, @Nullable Long postId, @Nullable ZonedDateTime postCreationDate, boolean hasStudentAnswer, @Nullable PlagiarismVerdict verdict,
            @Nullable ZonedDateTime verdictDate, @Nullable Long verdictById, @Nullable String verdictByLogin, @Nullable String verdictByFirstName,
            @Nullable String verdictByLastName, long plagiarismSubmissionCount, boolean createdByContinuousPlagiarismControl) {
        this(id, exercise, userOrNull(studentId, studentLogin, User.displayName(studentFirstName, studentLastName, studentLogin), null), postOrNull(postId, postCreationDate),
                verdict, verdictDate, userOrNull(verdictById, verdictByLogin, User.displayName(verdictByFirstName, verdictByLastName, verdictByLogin), null),
                toBoundedInt(plagiarismSubmissionCount), createdByContinuousPlagiarismControl, hasStudentAnswer);
    }

    private static @Nullable PlagiarismCaseUserDTO userOrNull(@Nullable Long id, @Nullable String login, @Nullable String name, @Nullable String visibleRegistrationNumber) {
        if (id == null) {
            return null;
        }
        return new PlagiarismCaseUserDTO(id, login, name, visibleRegistrationNumber);
    }

    private static int toBoundedInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.toIntExact(value);
    }

    private static @Nullable PlagiarismCasePostSummaryDTO postOrNull(@Nullable Long id, @Nullable ZonedDateTime creationDate) {
        if (id == null) {
            return null;
        }
        return new PlagiarismCasePostSummaryDTO(id, creationDate);
    }
}
