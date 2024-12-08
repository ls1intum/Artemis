package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseDeletionSummaryDTO(long numberOfBuilds, long numberOfCommunicationPosts, long numberOfAnswerPosts, long numberProgrammingExercises, long numberTextExercises,
        long numberFileUploadExercises, long numberModelingExercises, long numberQuizExercises, long numberExams, long numberLectures) {
}
