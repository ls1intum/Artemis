package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseDetailsDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise, List<QuizQuestionWithSolutionDTO> quizQuestions, Set<String> categories,
        Set<CompetencyExerciseLinkDTO> competencyLinks, List<GradingCriterionDTO> gradingCriteria, String channelName, Boolean testRunParticipationsExist, Boolean isEditable,
        Boolean canBeEvaluated) {

    /**
     * Converts a quiz exercise entity to the instructor-facing details DTO.
     *
     * @param quizExercise the quiz exercise entity
     * @return the corresponding quiz exercise details
     */
    public static QuizExerciseDetailsDTO of(QuizExercise quizExercise) {
        List<QuizQuestionWithSolutionDTO> questionDTOs = quizExercise.getQuizQuestions().stream().map(QuizQuestionWithSolutionDTO::of).toList();
        Set<String> categories = quizExercise.getCategories();
        Set<CompetencyExerciseLinkDTO> competencyExerciseLinkDTOs = null;
        if (Hibernate.isInitialized(quizExercise.getCompetencyLinks())) {
            competencyExerciseLinkDTOs = quizExercise.getCompetencyLinks().stream().map(CompetencyExerciseLinkDTO::of).collect(Collectors.toSet());
        }
        List<GradingCriterionDTO> gradingCriterionDTOs = null;
        if (Hibernate.isInitialized(quizExercise.getGradingCriteria())) {
            gradingCriterionDTOs = GradingCriterionDTO.ofAll(quizExercise.getGradingCriteria());
        }

        return new QuizExerciseDetailsDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise), questionDTOs, categories, competencyExerciseLinkDTOs, gradingCriterionDTOs,
                quizExercise.getChannelName(), quizExercise.getTestRunParticipationsExist(), null, null);
    }

    public static QuizExerciseDetailsDTO of(QuizExercise quizExercise, Boolean isEditable) {
        QuizExerciseDetailsDTO dto = of(quizExercise);
        return new QuizExerciseDetailsDTO(dto.quizExercise, dto.quizQuestions, dto.categories, dto.competencyLinks, dto.gradingCriteria, dto.channelName,
                dto.testRunParticipationsExist, isEditable, null);
    }

    /**
     * Converts a quiz exercise entity to the instructor-facing details DTO, including what the instructor may do with the quiz right now.
     *
     * @param quizExercise       the quiz exercise entity
     * @param isEditable         whether the quiz can still be edited
     * @param effectiveQuizEnded whether the quiz is over; for an exam quiz this is the end of the exam of all students rather than a due date
     * @param canBeEvaluated     whether the quiz can be evaluated now, so the client only offers the action when the server accepts it
     * @return the corresponding quiz exercise details
     */
    public static QuizExerciseDetailsDTO of(QuizExercise quizExercise, Boolean isEditable, boolean effectiveQuizEnded, boolean canBeEvaluated) {
        QuizExerciseDetailsDTO dto = of(quizExercise);
        return new QuizExerciseDetailsDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise, effectiveQuizEnded), dto.quizQuestions, dto.categories, dto.competencyLinks,
                dto.gradingCriteria, dto.channelName, dto.testRunParticipationsExist, isEditable, canBeEvaluated);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record CompetencyExerciseLinkDTO(double weight, boolean generatedByAi, CourseCompetencyDTO competency) {

    public static CompetencyExerciseLinkDTO of(CompetencyExerciseLink competencyExerciseLink) {
        return new CompetencyExerciseLinkDTO(competencyExerciseLink.getWeight(), competencyExerciseLink.isGeneratedByAi(),
                CourseCompetencyDTO.of(competencyExerciseLink.getCompetency()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record CourseCompetencyDTO(@JsonUnwrapped BaseCompetencyDTO baseCompetency, ZonedDateTime softDueDate, int masteryThreshold, boolean optional, boolean generatedByAi, String type) {

    public static CourseCompetencyDTO of(CourseCompetency courseCompetency) {
        String type = null;
        if (courseCompetency instanceof Competency) {
            type = "competency";
        }
        else if (courseCompetency instanceof Prerequisite) {
            type = "prerequisite";
        }
        return new CourseCompetencyDTO(BaseCompetencyDTO.of(courseCompetency), courseCompetency.getSoftDueDate(), courseCompetency.getMasteryThreshold(),
                courseCompetency.isOptional(), courseCompetency.isGeneratedByAi(), type);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record BaseCompetencyDTO(Long id, String title, String description, CompetencyTaxonomy taxonomy) {

    public static BaseCompetencyDTO of(CourseCompetency courseCompetency) {
        return new BaseCompetencyDTO(courseCompetency.getId(), courseCompetency.getTitle(), courseCompetency.getDescription(), courseCompetency.getTaxonomy());
    }
}
