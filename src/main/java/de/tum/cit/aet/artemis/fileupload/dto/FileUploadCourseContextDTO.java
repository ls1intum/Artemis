package de.tum.cit.aet.artemis.fileupload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * DTO representing the course context for a file upload exercise/submission.
 *
 * @param id               the ID of the course
 * @param title            the title of the course
 * @param shortName        the short name of the course
 * @param accuracyOfScores the accuracy of scores (number of decimal places) for the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadCourseContextDTO(Long id, String title, String shortName, Integer accuracyOfScores) {

    /**
     * Factory method to create a {@link FileUploadCourseContextDTO} from a {@link Course} entity.
     *
     * @param course the course entity to map, can be null
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadCourseContextDTO of(Course course) {
        if (course == null) {
            return null;
        }
        return new FileUploadCourseContextDTO(course.getId(), course.getTitle(), course.getShortName(), course.getAccuracyOfScores());
    }
}
