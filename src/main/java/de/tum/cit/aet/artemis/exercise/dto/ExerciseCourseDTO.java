package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;

/**
 * The course context the exercise views read off {@code exercise.course}.
 * <p>
 * Only the scalars the exercise pages evaluate: the id the client derives its access rights and every management link
 * from, the title it renders, the score precision the header and the result components round with, the complaint and
 * feedback-request settings the complaint forms gate on, the two Athena switches the assessment dashboard reads, and
 * the communication configuration the exercise pages use to decide whether to offer a channel. The course's own
 * collections (exercises, lectures, exams, competencies) were never serialized here - they are lazy and stayed
 * uninitialized on these paths.
 *
 * @param id                                    the id of the course
 * @param title                                 the title of the course
 * @param shortName                             the short name of the course
 * @param testCourse                            whether the course is a test course
 * @param accuracyOfScores                      the number of decimal places scores are rounded to
 * @param maxComplaints                         the number of complaints a student may file per course
 * @param maxTeamComplaints                     the number of complaints a team may file per course
 * @param maxComplaintTimeDays                  the number of days a complaint may be filed after the result
 * @param maxComplaintTextLimit                 the character limit of a complaint text
 * @param maxComplaintResponseTextLimit         the character limit of a complaint response text
 * @param complaintsEnabled                     whether complaints are enabled in the course
 * @param requestMoreFeedbackEnabled            whether more-feedback requests are enabled in the course
 * @param maxRequestMoreFeedbackTimeDays        the number of days a more-feedback request may be filed after the result
 * @param presentationScore                     the number of presentations counted towards the course score
 * @param timeZone                              the time zone dates are displayed in
 * @param athenaGradingFeedbackEnabled          whether Athena grading feedback is enabled in the course
 * @param athenaFormativeFeedbackEnabled        whether Athena formative feedback is enabled in the course
 * @param courseInformationSharingConfiguration the communication and messaging configuration of the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseCourseDTO(long id, @Nullable String title, @Nullable String shortName, boolean testCourse, @Nullable Integer accuracyOfScores,
        @Nullable Integer maxComplaints, @Nullable Integer maxTeamComplaints, int maxComplaintTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit,
        boolean complaintsEnabled, boolean requestMoreFeedbackEnabled, int maxRequestMoreFeedbackTimeDays, @Nullable Integer presentationScore, @Nullable String timeZone,
        boolean athenaGradingFeedbackEnabled, boolean athenaFormativeFeedbackEnabled, @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration) {

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
        return new ExerciseCourseDTO(course.getId(), course.getTitle(), course.getShortName(), course.isTestCourse(), course.getAccuracyOfScores(), course.getMaxComplaints(),
                course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(), course.getMaxComplaintTextLimit(), course.getMaxComplaintResponseTextLimit(),
                course.getComplaintsEnabled(), course.getRequestMoreFeedbackEnabled(), course.getMaxRequestMoreFeedbackTimeDays(), course.getPresentationScore(),
                course.getTimeZone(), course.isAthenaGradingFeedbackEnabled(), course.isAthenaFormativeFeedbackEnabled(), course.getCourseInformationSharingConfiguration());
    }
}
