package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    public PlagiarismCaseOverviewDTO(Long id, PlagiarismCaseExerciseDTO exercise, Long studentId, String studentLogin, String studentFirstName, String studentLastName, Long postId,
            ZonedDateTime postCreationDate, boolean hasStudentAnswer, PlagiarismVerdict verdict, ZonedDateTime verdictDate, Long verdictById, String verdictByLogin,
            String verdictByFirstName, String verdictByLastName, long plagiarismSubmissionCount, boolean createdByContinuousPlagiarismControl) {
        this(id, exercise, userOrNull(studentId, studentLogin, fullName(studentFirstName, studentLastName), null), postOrNull(postId, postCreationDate), verdict, verdictDate,
                userOrNull(verdictById, verdictByLogin, fullName(verdictByFirstName, verdictByLastName), null), toBoundedInt(plagiarismSubmissionCount),
                createdByContinuousPlagiarismControl, hasStudentAnswer);
    }

    private static PlagiarismCaseUserDTO userOrNull(Long id, String login, String name, String visibleRegistrationNumber) {
        if (id == null) {
            return null;
        }
        return new PlagiarismCaseUserDTO(id, login, name, visibleRegistrationNumber);
    }

    private static String fullName(String firstName, String lastName) {
        boolean hasFirstName = firstName != null && !firstName.isEmpty();
        boolean hasLastName = lastName != null && !lastName.isEmpty();
        if (hasFirstName && hasLastName) {
            return firstName + " " + lastName;
        }
        if (hasFirstName) {
            return firstName;
        }
        if (hasLastName) {
            return lastName;
        }
        return null;
    }

    private static int toBoundedInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.toIntExact(value);
    }

    private static PlagiarismCasePostSummaryDTO postOrNull(Long id, ZonedDateTime creationDate) {
        if (id == null) {
            return null;
        }
        return new PlagiarismCasePostSummaryDTO(id, creationDate);
    }
}
