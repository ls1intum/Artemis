package de.tum.in.www1.artemis.repository.converters;

import jakarta.persistence.Converter;

import de.tum.in.www1.artemis.domain.quiz.AnswerOption;

@Converter
public class ObjectListConverter extends JpaConverterJson<AnswerOption> {

    public ObjectListConverter() {
        super(AnswerOption.class);
    }
}
