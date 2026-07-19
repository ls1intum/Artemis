package de.tum.cit.aet.artemis.quiz.dto.participation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(oneOf = { StudentQuizParticipationWithSolutionsDTO.class, StudentQuizParticipationWithQuestionsDTO.class, StudentQuizParticipationWithoutQuestionsDTO.class })
public sealed interface StudentQuizParticipationDTO
        permits StudentQuizParticipationWithoutQuestionsDTO, StudentQuizParticipationWithQuestionsDTO, StudentQuizParticipationWithSolutionsDTO {

}
