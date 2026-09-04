package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseDetailsDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise, List<QuizQuestionWithSolutionDTO> quizQuestions, Set<String> categories,
        Set<CompetencyExerciseLinkDTO> competencyLinks, Set<GradingCriterionDTO> gradingCriteria, String channelName, Boolean testRunParticipationsExist, Boolean isEditable) {

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
        Set<GradingCriterionDTO> gradingCriterionDTOs = null;
        if (Hibernate.isInitialized(quizExercise.getGradingCriteria())) {
            gradingCriterionDTOs = quizExercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }

        return new QuizExerciseDetailsDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise), questionDTOs, categories, competencyExerciseLinkDTOs, gradingCriterionDTOs,
                quizExercise.getChannelName(), quizExercise.getTestRunParticipationsExist(), null);
    }

    public static QuizExerciseDetailsDTO of(QuizExercise quizExercise, Boolean isEditable) {
        QuizExerciseDetailsDTO dto = of(quizExercise);
        return new QuizExerciseDetailsDTO(dto.quizExercise, dto.quizQuestions, dto.categories, dto.competencyLinks, dto.gradingCriteria, dto.channelName,
                dto.testRunParticipationsExist, isEditable);
    }

    public static QuizExerciseDetailsDTO of(QuizExercise quizExercise, Boolean isEditable, boolean effectiveQuizEnded) {
        QuizExerciseDetailsDTO dto = of(quizExercise);
        return new QuizExerciseDetailsDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise, effectiveQuizEnded), dto.quizQuestions, dto.categories, dto.competencyLinks,
                dto.gradingCriteria, dto.channelName, dto.testRunParticipationsExist, isEditable);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record CompetencyExerciseLinkDTO(double weight, CourseCompetencyDTO competency) {

    public static CompetencyExerciseLinkDTO of(CompetencyExerciseLink competencyExerciseLink) {
        return new CompetencyExerciseLinkDTO(competencyExerciseLink.getWeight(), CourseCompetencyDTO.of(competencyExerciseLink.getCompetency()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record CourseCompetencyDTO(@JsonUnwrapped BaseCompetencyDTO baseCompetency, ZonedDateTime softDueDate, int masteryThreshold, boolean optional, String type) {

    public static CourseCompetencyDTO of(CourseCompetency courseCompetency) {
        String type = null;
        if (courseCompetency instanceof Competency) {
            type = "competency";
        }
        else if (courseCompetency instanceof Prerequisite) {
            type = "prerequisite";
        }
        return new CourseCompetencyDTO(BaseCompetencyDTO.of(courseCompetency), courseCompetency.getSoftDueDate(), courseCompetency.getMasteryThreshold(),
                courseCompetency.isOptional(), type);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record BaseCompetencyDTO(Long id, String title, String description, CompetencyTaxonomy taxonomy) {

    public static BaseCompetencyDTO of(CourseCompetency courseCompetency) {
        return new BaseCompetencyDTO(courseCompetency.getId(), courseCompetency.getTitle(), courseCompetency.getDescription(), courseCompetency.getTaxonomy());
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record GradingCriterionDTO(Long id, String title, Set<GradingInstructionDTO> structuredGradingInstructions) {

    public static GradingCriterionDTO of(GradingCriterion gradingCriterion) {
        Set<GradingInstructionDTO> gradingInstructionDTOs = Set.of();
        if (Hibernate.isInitialized(gradingCriterion.getStructuredGradingInstructions())) {
            gradingInstructionDTOs = gradingCriterion.getStructuredGradingInstructions().stream().map(GradingInstructionDTO::of).collect(Collectors.toSet());
        }
        return new GradingCriterionDTO(gradingCriterion.getId(), gradingCriterion.getTitle(), gradingInstructionDTOs);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record GradingInstructionDTO(Long id, double credits, String gradingScale, String instructionDescription, String feedback, int usageCount) {

    public static GradingInstructionDTO of(GradingInstruction gradingInstruction) {
        return new GradingInstructionDTO(gradingInstruction.getId(), gradingInstruction.getCredits(), gradingInstruction.getGradingScale(),
                gradingInstruction.getInstructionDescription(), gradingInstruction.getFeedback(), gradingInstruction.getUsageCount());
    }
}
