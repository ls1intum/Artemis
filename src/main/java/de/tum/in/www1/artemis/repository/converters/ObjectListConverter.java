package de.tum.in.www1.artemis.repository.converters;

import jakarta.persistence.Converter;

import de.tum.in.www1.artemis.domain.quiz.AnswerOptionDTO;

@Converter
public class ObjectListConverter extends JpaConverterJson<AnswerOptionDTO> {

    public ObjectListConverter() {
        super(AnswerOptionDTO.class);
    }
}
