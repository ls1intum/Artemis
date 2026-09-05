package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.dto.CourseForQuizExerciseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Light, cycle-free course projection embedded in programming-exercise responses.
 * <p>
 * The unchanged Angular client reads {@code exercise.course} for display links, for course-level complaint
 * configuration and, on the programming grading form, for {@code course.presentationScore}. It carries every
 * component of {@link CourseForQuizExerciseDTO} plus {@code presentationScore}, which that shared record does not
 * have and which must not be added to it.
 * <p>
 * It also carries the course-level flags that the {@link Course} entity put on the wire whenever it was serialized
 * nested under an exercise ({@code enrollmentEnabled}, {@code unenrollmentEnabled}, {@code onboardingDone},
 * {@code restrictedAthenaModulesAccess}, {@code learningPathsEnabled}, {@code gradeRelevant},
 * {@code dataRetentionHold} and {@code trainingEnabled}). They matter because this record is reached from the
 * {@code @AllowedTools(SCORPIO)} latest-result route, whose out-of-repo IntelliJ client cannot be grepped for
 * readers, so the payload has to stay a superset of the entity payload it replaced.
 * <p>
 * The component list is therefore the complete set of serializable {@link Course} scalars, taken from the entity
 * rather than from a fixture dump: {@code NON_EMPTY} hides every property a fixture happens to leave at its default,
 * so a wire diff can only prove that a populated key survived, never that an unpopulated one exists. What the entity
 * serialized and this record deliberately does not carry is only:
 * <ul>
 * <li>the associations — {@code exercises}, {@code lectures}, {@code exams}, {@code competencies},
 * {@code prerequisites}, {@code learningPaths}, {@code tutorialGroups}, {@code tutorialGroupsConfiguration},
 * {@code organizations}, {@code exerciseVariantGroups}, {@code onlineCourseConfiguration} and
 * {@code courseConfiguration}. None of them is initialized by an exercise fetch graph, so the entity dropped them
 * here as well; re-emitting one would either add a query per response or re-enter the exercise cycle.</li>
 * <li>the {@code @Transient} counters an instructor dashboard fills in ({@code numberOfStudents},
 * {@code numberOfTeachingAssistants}, {@code numberOfEditors}, {@code numberOfInstructors},
 * {@code numberOfTutorialGroups}, {@code numberOfCompetencies}, {@code numberOfPrerequisites} and
 * {@code numberOfAcceptedFaqs}). No path that reaches an exercise sets them, so they were {@code null} — and thus
 * absent — on the entity wire too.</li>
 * </ul>
 *
 * @param id                                             the course id
 * @param title                                          the course title
 * @param description                                    the course description
 * @param shortName                                      the course short name used in API paths
 * @param startDate                                      when the course starts
 * @param endDate                                        when the course ends
 * @param enrollmentStartDate                            when enrollment opens
 * @param enrollmentEndDate                              when enrollment closes
 * @param unenrollmentEndDate                            when unenrollment closes
 * @param semester                                       the semester the course belongs to
 * @param testCourse                                     whether this is a test course
 * @param language                                       the course language
 * @param defaultProgrammingLanguage                     the default programming language of new exercises
 * @param onlineCourse                                   whether this is an online course
 * @param courseInformationSharingConfiguration          the communication/messaging configuration
 * @param maxComplaints                                  the maximum number of complaints per student
 * @param maxTeamComplaints                              the maximum number of complaints per team
 * @param maxComplaintTimeDays                           the complaint window in days
 * @param maxRequestMoreFeedbackTimeDays                 the more-feedback-request window in days
 * @param maxComplaintTextLimit                          the maximum complaint text length
 * @param maxComplaintResponseTextLimit                  the maximum complaint response text length
 * @param complaintsEnabled                              whether complaints are enabled
 * @param requestMoreFeedbackEnabled                     whether more-feedback requests are enabled
 * @param accuracyOfScores                               the number of decimal places used for scores
 * @param presentationScore                              the course presentation score (read by the grading form)
 * @param enrollmentEnabled                              whether students may enroll themselves
 * @param unenrollmentEnabled                            whether students may unenroll themselves
 * @param onboardingDone                                 whether the course onboarding was completed
 * @param restrictedAthenaModulesAccess                  whether the course may use restricted Athena modules
 * @param learningPathsEnabled                           whether learning paths are enabled
 * @param gradeRelevant                                  whether the course counts towards a grade
 * @param dataRetentionHold                              whether the course data is held back from the retention job
 * @param trainingEnabled                                whether training is enabled
 * @param color                                          the course colour used by the client headers
 * @param courseIcon                                     the path of the course icon
 * @param enrollmentConfirmationMessage                  the message shown after a student enrolled
 * @param courseArchivePath                              the path of the course archive, when one was created
 * @param maxPoints                                      the reachable points of the whole course
 * @param timeZone                                       the course time zone used by the tutorial-group feature
 * @param courseInformationSharingMessagingCodeOfConduct the messaging code of conduct
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseCourseDTO(Long id, String title, String description, String shortName, ZonedDateTime startDate, ZonedDateTime endDate,
        ZonedDateTime enrollmentStartDate, ZonedDateTime enrollmentEndDate, ZonedDateTime unenrollmentEndDate, String semester, boolean testCourse, Language language,
        ProgrammingLanguage defaultProgrammingLanguage, Boolean onlineCourse, CourseInformationSharingConfiguration courseInformationSharingConfiguration, Integer maxComplaints,
        Integer maxTeamComplaints, int maxComplaintTimeDays, int maxRequestMoreFeedbackTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit,
        boolean complaintsEnabled, boolean requestMoreFeedbackEnabled, Integer accuracyOfScores, Integer presentationScore, Boolean enrollmentEnabled, boolean unenrollmentEnabled,
        boolean onboardingDone, boolean restrictedAthenaModulesAccess, boolean learningPathsEnabled, boolean gradeRelevant, boolean dataRetentionHold, boolean trainingEnabled,
        String color, String courseIcon, String enrollmentConfirmationMessage, String courseArchivePath, Integer maxPoints, String timeZone,
        String courseInformationSharingMessagingCodeOfConduct) implements Serializable {

    /**
     * Creates a {@link ProgrammingExerciseCourseDTO} from a {@link Course}.
     *
     * @param course the course to project (may be {@code null})
     * @return the projected course, or {@code null} if the input was {@code null}
     */
    public static ProgrammingExerciseCourseDTO of(Course course) {
        if (course == null) {
            return null;
        }
        return new ProgrammingExerciseCourseDTO(course.getId(), course.getTitle(), course.getDescription(), course.getShortName(), course.getStartDate(), course.getEndDate(),
                course.getEnrollmentStartDate(), course.getEnrollmentEndDate(), course.getUnenrollmentEndDate(), course.getSemester(), course.isTestCourse(), course.getLanguage(),
                course.getDefaultProgrammingLanguage(), course.isOnlineCourse(), course.getCourseInformationSharingConfiguration(), course.getMaxComplaints(),
                course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(), course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxComplaintTextLimit(),
                course.getMaxComplaintResponseTextLimit(), course.getComplaintsEnabled(), course.getRequestMoreFeedbackEnabled(), course.getAccuracyOfScores(),
                course.getPresentationScore(), course.isEnrollmentEnabled(), course.isUnenrollmentEnabled(), course.isOnboardingDone(), course.getRestrictedAthenaModulesAccess(),
                course.getLearningPathsEnabled(), course.isGradeRelevant(), course.isDataRetentionHold(), course.isTrainingEnabled(), course.getColor(), course.getCourseIcon(),
                course.getEnrollmentConfirmationMessage(), course.getCourseArchivePath(), course.getMaxPoints(), course.getTimeZone(),
                course.getCourseInformationSharingMessagingCodeOfConduct());
    }

    /**
     * Resolves the {@code course} slot of an exercise response. An exam exercise carries its course inside the
     * {@code exerciseGroup.exam} chain instead, mirroring the entity, whose {@code course} member is null for exam
     * exercises. Flattening either slot to a bare id breaks access rights and exam navigation, so both response
     * records resolve them through this one method.
     *
     * @param exercise the exercise being mapped
     * @return the nested course, or {@code null} for an exam exercise or an uninitialized course
     */
    public static ProgrammingExerciseCourseDTO ofCourseExercise(ProgrammingExercise exercise) {
        if (!exercise.isCourseExercise()) {
            return null;
        }
        Course courseEntity = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (courseEntity == null || !Hibernate.isInitialized(courseEntity)) {
            return null;
        }
        return of(courseEntity);
    }
}
