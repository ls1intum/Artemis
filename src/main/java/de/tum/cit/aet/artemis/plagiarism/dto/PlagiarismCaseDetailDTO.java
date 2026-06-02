package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseDetailDTO(Long id, PlagiarismCaseExerciseDTO exercise, PlagiarismCaseUserDTO student, PlagiarismCasePostSummaryDTO post, PlagiarismVerdict verdict,
        ZonedDateTime verdictDate, PlagiarismCaseUserDTO verdictBy, int plagiarismSubmissionCount, boolean createdByContinuousPlagiarismControl, String verdictMessage,
        int verdictPointDeduction, List<PlagiarismSubmissionForCaseDTO> plagiarismSubmissions) {

    /**
     * JPQL constructor for detail projections. It keeps optional nested DTOs absent when the joined entity is absent.
     *
     * @param id                                   the plagiarism case id
     * @param exercise                             the exercise DTO
     * @param studentId                            the affected student id
     * @param studentLogin                         the affected student login
     * @param studentFirstName                     the affected student's first name
     * @param studentLastName                      the affected student's last name
     * @param postId                               the post id
     * @param postCreationDate                     the post creation date
     * @param verdict                              the plagiarism verdict
     * @param verdictDate                          the verdict date
     * @param verdictById                          the verdict author id
     * @param verdictByLogin                       the verdict author login
     * @param verdictByFirstName                   the verdict author's first name
     * @param verdictByLastName                    the verdict author's last name
     * @param plagiarismSubmissionCount            the number of submissions attached to the plagiarism case
     * @param createdByContinuousPlagiarismControl whether the case was created by continuous plagiarism control
     * @param verdictMessage                       the verdict message
     * @param verdictPointDeduction                the verdict point deduction
     */
    public PlagiarismCaseDetailDTO(Long id, PlagiarismCaseExerciseDTO exercise, Long studentId, String studentLogin, String studentFirstName, String studentLastName, Long postId,
            ZonedDateTime postCreationDate, PlagiarismVerdict verdict, ZonedDateTime verdictDate, Long verdictById, String verdictByLogin, String verdictByFirstName,
            String verdictByLastName, long plagiarismSubmissionCount, boolean createdByContinuousPlagiarismControl, String verdictMessage, int verdictPointDeduction) {
        this(id, exercise, userOrNull(studentId, studentLogin, fullName(studentFirstName, studentLastName), null), postOrNull(postId, postCreationDate), verdict, verdictDate,
                userOrNull(verdictById, verdictByLogin, fullName(verdictByFirstName, verdictByLastName), null), toBoundedInt(plagiarismSubmissionCount),
                createdByContinuousPlagiarismControl, verdictMessage, verdictPointDeduction, null);
    }

    /**
     * Maps a plagiarism case entity to the instructor detail DTO.
     *
     * @param plagiarismCase the plagiarism case entity
     * @return the DTO representation
     */
    public static PlagiarismCaseDetailDTO ofForInstructor(PlagiarismCase plagiarismCase) {
        return of(plagiarismCase);
    }

    /**
     * Maps a plagiarism case entity to the student detail DTO.
     *
     * @param plagiarismCase the plagiarism case entity
     * @return the DTO representation
     */
    public static PlagiarismCaseDetailDTO ofForStudent(PlagiarismCase plagiarismCase) {
        return of(plagiarismCase);
    }

    private static PlagiarismCaseDetailDTO of(PlagiarismCase plagiarismCase) {
        if (plagiarismCase == null) {
            return null;
        }

        List<PlagiarismSubmissionForCaseDTO> plagiarismSubmissions = null;
        if (plagiarismCase.getPlagiarismSubmissions() != null && Hibernate.isInitialized(plagiarismCase.getPlagiarismSubmissions())) {
            plagiarismSubmissions = plagiarismCase.getPlagiarismSubmissions().stream().map(PlagiarismSubmissionForCaseDTO::fromSubmissionForCase).toList();
        }
        int plagiarismSubmissionCount = plagiarismSubmissions != null ? plagiarismSubmissions.size() : 0;

        return new PlagiarismCaseDetailDTO(plagiarismCase.getId(), PlagiarismCaseExerciseDTO.fromExercise(plagiarismCase.getExercise()),
                PlagiarismCaseUserDTO.fromUser(plagiarismCase.getStudent()), PlagiarismCasePostSummaryDTO.fromPost(plagiarismCase.getPost()), plagiarismCase.getVerdict(),
                plagiarismCase.getVerdictDate(), PlagiarismCaseUserDTO.fromUser(plagiarismCase.getVerdictBy()), plagiarismSubmissionCount,
                plagiarismCase.isCreatedByContinuousPlagiarismControl(), plagiarismCase.getVerdictMessage(), plagiarismCase.getVerdictPointDeduction(), plagiarismSubmissions);
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
