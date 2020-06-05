package de.tum.in.www1.artemis.domain.exam.dto.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.dto.ExamInstructorDTO;

@Component
public class ExamInstructorDTOConverter implements Converter<Exam, ExamInstructorDTO> {

    @Override
    public ExamInstructorDTO convert(Exam source) {
        return new ExamInstructorDTO(source.getId(), source.getCourse(), source.getStudentExams(), source.getExerciseGroups(), source.getReleaseDate(), source.getDueDate(),
                source.getStartText(), source.getEndText(), source.getConfirmationStartText(), source.getConfirmationEndText(), source.getMaxScore());
    }
}
