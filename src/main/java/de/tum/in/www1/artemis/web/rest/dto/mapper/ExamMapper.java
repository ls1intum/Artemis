package de.tum.in.www1.artemis.web.rest.dto.mapper;

import java.time.temporal.ChronoUnit;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.web.rest.dto.response.ExamResponseDTO;

@Mapper(componentModel = "spring")
public interface ExamMapper {

    ExamResponseDTO examToExamResponseDto(Exam exam);

    @AfterMapping
    default void calculateDuration(Exam exam, @MappingTarget ExamResponseDTO result) {
        if (exam.getStartDate() != null && exam.getEndDate() != null) {
            result.durationInMinutes = ChronoUnit.MINUTES.between(exam.getStartDate(), exam.getEndDate());
        }
    }

}
