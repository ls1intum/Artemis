package de.tum.cit.aet.artemis.hyperion.dto.quiz;

public enum AiQuestionSubtype {
    MULTI_CORRECT,   // multiple-choice (>=1 correct)
    SINGLE_CORRECT,  // single-choice (exactly 1 correct)
    TRUE_FALSE       // two options, only one correct
}
