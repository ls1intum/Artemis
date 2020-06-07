package de.tum.in.www1.artemis.domain.exam.dto.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.web.rest.dto.ExamDTO;

@Component
public class ExamDTOConverter implements Converter<Exam, ExamDTO> {

    @Override
    public ExamDTO convert(Exam source) {
        return new ExamDTO(source.getId(), source.getCourse(), source.getExerciseGroups(), source.getReleaseDate(), source.getDueDate(), source.getStartText(), source.getEndText(),
                source.getConfirmationStartText(), source.getConfirmationEndText());
    }
}
