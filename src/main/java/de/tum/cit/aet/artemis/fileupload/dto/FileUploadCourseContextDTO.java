package de.tum.cit.aet.artemis.fileupload.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * DTO representing the course context for a file upload exercise/submission.
 *
 * @param id                             the ID of the course
 * @param title                          the title of the course
 * @param shortName                      the short name of the course
 * @param accuracyOfScores               the accuracy of scores (number of decimal places) for the course
 * @param maxComplaints                  the maximum number of complaints per student
 * @param maxTeamComplaints              the maximum number of complaints per team
 * @param maxComplaintTimeDays           the complaint submission period in days
 * @param maxRequestMoreFeedbackTimeDays the more-feedback request period in days
 * @param maxComplaintTextLimit          the maximum complaint text length
 * @param maxComplaintResponseTextLimit  the maximum complaint response text length
 * @param complaintsEnabled              whether complaints are enabled
 * @param requestMoreFeedbackEnabled     whether more-feedback requests are enabled
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadCourseContextDTO(Long id, String title, String shortName, @Nullable Integer accuracyOfScores, @Nullable Integer maxComplaints,
        @Nullable Integer maxTeamComplaints, int maxComplaintTimeDays, int maxRequestMoreFeedbackTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit,
        boolean complaintsEnabled, boolean requestMoreFeedbackEnabled) {

    /**
     * Factory method to create a {@link FileUploadCourseContextDTO} from a {@link Course} entity.
     *
     * @param course the course entity to map, can be null
     * @return the mapped DTO, or null if the input was null
     */
    public static @Nullable FileUploadCourseContextDTO of(@Nullable Course course) {
        if (course == null) {
            return null;
        }
        return new FileUploadCourseContextDTO(course.getId(), course.getTitle(), course.getShortName(), course.getAccuracyOfScores(), course.getMaxComplaints(),
                course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(), course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxComplaintTextLimit(),
                course.getMaxComplaintResponseTextLimit(), course.getComplaintsEnabled(), course.getRequestMoreFeedbackEnabled());
    }
}
