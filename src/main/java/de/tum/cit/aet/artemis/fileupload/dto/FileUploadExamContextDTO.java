package de.tum.cit.aet.artemis.fileupload.dto;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * DTO representing the exam context for a file upload exercise/submission.
 *
 * @param id     the ID of the exam
 * @param course the course context of the exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadExamContextDTO(Long id, FileUploadCourseContextDTO course) {

    /**
     * Factory method to create a {@link FileUploadExamContextDTO} from an {@link Exam} entity.
     *
     * @param exam the exam entity to map, can be null
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadExamContextDTO of(Exam exam) {
        if (exam == null) {
            return null;
        }
        Course course = exam.getCourse();
        FileUploadCourseContextDTO courseDTO = course != null && Hibernate.isInitialized(course) ? FileUploadCourseContextDTO.of(course) : null;
        return new FileUploadExamContextDTO(exam.getId(), courseDTO);
    }
}
