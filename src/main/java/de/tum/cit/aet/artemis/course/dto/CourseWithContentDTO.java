package de.tum.cit.aet.artemis.course.dto;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyResponseDTO;
import de.tum.cit.aet.artemis.course.domain.Course;

/** Course management data with the content required by LTI content selection. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseWithContentDTO(@JsonUnwrapped CourseManagementDTO course, Set<CourseManagementExerciseDTO> exercises, Set<LectureForCourseManagementDTO> lectures,
        Set<CourseCompetencyResponseDTO> competencies, Set<CoursePrerequisiteDTO> prerequisites) {

    /** Maps the explicitly fetched course content graph. */
    public static CourseWithContentDTO of(Course course) {
        return new CourseWithContentDTO(CourseManagementDTO.of(course), course.getExercises().stream().map(CourseManagementExerciseDTO::of).collect(Collectors.toSet()),
                course.getLectures().stream().map(LectureForCourseManagementDTO::of).collect(Collectors.toSet()),
                course.getCompetencies().stream().map(CourseCompetencyResponseDTO::of).collect(Collectors.toSet()),
                course.getPrerequisites().stream().map(CoursePrerequisiteDTO::of).collect(Collectors.toSet()));
    }
}
