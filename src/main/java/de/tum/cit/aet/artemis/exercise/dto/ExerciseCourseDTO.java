package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * The course context the exercise views read off {@code exercise.course}.
 * <p>
 * The exercise pages themselves only evaluate a handful of these: the id the client derives its access rights and every
 * management link from, the title it renders, the score precision the header and the result components round with, the
 * complaint and feedback-request settings the complaint forms gate on, the two Athena switches the assessment dashboard
 * reads, and the communication configuration the exercise pages use to decide whether to offer a channel.
 * <p>
 * The remaining components exist because {@code GET exercises/{exerciseId}} is also reachable with a SCORPIO tool token
 * from a client that cannot be audited in this tree. That route used to hand out the {@code Course} entity, so every
 * scalar the entity serialized is repeated here, the same superset {@link de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseCourseDTO}
 * already keeps for the SCORPIO latest-result route. The set was read off the entity rather than off a fixture dump,
 * because {@code NON_EMPTY} hides every property a fixture leaves at its default. What the entity serialized here and
 * this record deliberately does not carry is only:
 * <ul>
 * <li>the associations - {@code exercises}, {@code lectures}, {@code exams}, {@code competencies},
 * {@code prerequisites}, {@code learningPaths}, {@code tutorialGroups}, {@code tutorialGroupsConfiguration},
 * {@code organizations}, {@code exerciseVariantGroups}, {@code onlineCourseConfiguration} and
 * {@code courseConfiguration}. None of them is initialized by an exercise fetch graph, so the entity dropped them here
 * as well; re-emitting one would cost a select per response or re-enter the exercise cycle.</li>
 * <li>the {@code @Transient} counters an instructor dashboard fills in ({@code numberOfStudents},
 * {@code numberOfTeachingAssistants}, {@code numberOfEditors}, {@code numberOfInstructors},
 * {@code numberOfTutorialGroups}, {@code numberOfCompetencies}, {@code numberOfPrerequisites},
 * {@code numberOfAcceptedFaqs}) and the two orchestration overrides {@code debounceWindowSecondsOverride} and
 * {@code maxDailyOrchestrationOverride}. All of them read {@code null} on a course reached through an exercise, so
 * {@code NON_EMPTY} left them off the entity wire here too.</li>
 * </ul>
 * {@code gradeRelevant}, {@code dataRetentionHold} and {@code autoOrchestratorEnabled} are carried even though they are
 * read off the lazy {@code CourseConfiguration}: their getters return a constant while it is uninitialized, so they cost
 * nothing, and being primitives {@code NON_EMPTY} put them on the entity wire at exactly those constants.
 *
 * @param id                                             the id of the course
 * @param title                                          the title of the course
 * @param description                                    the description of the course
 * @param shortName                                      the short name of the course
 * @param startDate                                      the start date of the course
 * @param endDate                                        the end date of the course
 * @param enrollmentStartDate                            the date enrollment opens
 * @param enrollmentEndDate                              the date enrollment closes
 * @param unenrollmentEndDate                            the date unenrollment closes
 * @param semester                                       the semester the course runs in
 * @param testCourse                                     whether the course is a test course
 * @param language                                       the language of the course
 * @param defaultProgrammingLanguage                     the programming language new programming exercises default to
 * @param onlineCourse                                   whether the course is an online (LTI) course
 * @param accuracyOfScores                               the number of decimal places scores are rounded to
 * @param maxComplaints                                  the number of complaints a student may file per course
 * @param maxTeamComplaints                              the number of complaints a team may file per course
 * @param maxComplaintTimeDays                           the number of days a complaint may be filed after the result
 * @param maxComplaintTextLimit                          the character limit of a complaint text
 * @param maxComplaintResponseTextLimit                  the character limit of a complaint response text
 * @param complaintsEnabled                              whether complaints are enabled in the course
 * @param requestMoreFeedbackEnabled                     whether more-feedback requests are enabled in the course
 * @param maxRequestMoreFeedbackTimeDays                 the number of days a more-feedback request may be filed after the result
 * @param presentationScore                              the number of presentations counted towards the course score
 * @param maxPoints                                      the maximum number of points reachable in the course
 * @param timeZone                                       the time zone dates are displayed in
 * @param color                                          the colour the course is rendered in
 * @param courseIcon                                     the icon of the course
 * @param enrollmentEnabled                              whether students may enroll themselves
 * @param enrollmentConfirmationMessage                  the message shown on enrollment
 * @param unenrollmentEnabled                            whether students may unenroll themselves
 * @param onboardingDone                                 whether the course onboarding was completed
 * @param learningPathsEnabled                           whether learning paths are enabled in the course
 * @param gradeRelevant                                  whether the course counts towards a grade
 * @param dataRetentionHold                              whether the course data is held back from the retention job
 * @param autoOrchestratorEnabled                        whether auto-orchestration is enabled for the course
 * @param trainingEnabled                                whether training is enabled in the course
 * @param courseArchivePath                              the path of the course archive
 * @param athenaGradingFeedbackEnabled                   whether Athena grading feedback is enabled in the course
 * @param athenaFormativeFeedbackEnabled                 whether Athena formative feedback is enabled in the course
 * @param courseInformationSharingConfiguration          the communication and messaging configuration of the course
 * @param courseInformationSharingMessagingCodeOfConduct the code of conduct shown before messaging
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseCourseDTO(long id, @Nullable String title, @Nullable String description, @Nullable String shortName, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate, @Nullable ZonedDateTime enrollmentStartDate, @Nullable ZonedDateTime enrollmentEndDate, @Nullable ZonedDateTime unenrollmentEndDate,
        @Nullable String semester, boolean testCourse, @Nullable Language language, @Nullable ProgrammingLanguage defaultProgrammingLanguage, boolean onlineCourse,
        @Nullable Integer accuracyOfScores, @Nullable Integer maxComplaints, @Nullable Integer maxTeamComplaints, int maxComplaintTimeDays, int maxComplaintTextLimit,
        int maxComplaintResponseTextLimit, boolean complaintsEnabled, boolean requestMoreFeedbackEnabled, int maxRequestMoreFeedbackTimeDays, @Nullable Integer presentationScore,
        @Nullable Integer maxPoints, @Nullable String timeZone, @Nullable String color, @Nullable String courseIcon, @Nullable Boolean enrollmentEnabled,
        @Nullable String enrollmentConfirmationMessage, boolean unenrollmentEnabled, boolean onboardingDone, boolean learningPathsEnabled, boolean gradeRelevant,
        boolean dataRetentionHold, boolean autoOrchestratorEnabled, boolean trainingEnabled, @Nullable String courseArchivePath, boolean athenaGradingFeedbackEnabled,
        boolean athenaFormativeFeedbackEnabled, @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration,
        @Nullable String courseInformationSharingMessagingCodeOfConduct) {

    /**
     * Maps a course to the context the exercise views need, without touching any of its associations.
     *
     * @param course the course, may be {@code null} for an exercise whose course is not loaded
     * @return the course context, or {@code null} when no course was given
     */
    public static @Nullable ExerciseCourseDTO of(@Nullable Course course) {
        if (course == null) {
            return null;
        }
        return new ExerciseCourseDTO(course.getId(), course.getTitle(), course.getDescription(), course.getShortName(), course.getStartDate(), course.getEndDate(),
                course.getEnrollmentStartDate(), course.getEnrollmentEndDate(), course.getUnenrollmentEndDate(), course.getSemester(), course.isTestCourse(), course.getLanguage(),
                course.getDefaultProgrammingLanguage(), course.isOnlineCourse(), course.getAccuracyOfScores(), course.getMaxComplaints(), course.getMaxTeamComplaints(),
                course.getMaxComplaintTimeDays(), course.getMaxComplaintTextLimit(), course.getMaxComplaintResponseTextLimit(), course.getComplaintsEnabled(),
                course.getRequestMoreFeedbackEnabled(), course.getMaxRequestMoreFeedbackTimeDays(), course.getPresentationScore(), course.getMaxPoints(), course.getTimeZone(),
                course.getColor(), course.getCourseIcon(), course.isEnrollmentEnabled(), course.getEnrollmentConfirmationMessage(), course.isUnenrollmentEnabled(),
                course.isOnboardingDone(), course.getLearningPathsEnabled(), course.isGradeRelevant(), course.isDataRetentionHold(), course.getAutoOrchestratorEnabled(),
                course.isTrainingEnabled(), course.getCourseArchivePath(), course.isAthenaGradingFeedbackEnabled(), course.isAthenaFormativeFeedbackEnabled(),
                course.getCourseInformationSharingConfiguration(), course.getCourseInformationSharingMessagingCodeOfConduct());
    }
}
