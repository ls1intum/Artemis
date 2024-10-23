package de.tum.cit.aet.artemis.exercise.api;

import java.util.Set;

import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;

// This is a very simple implementation, and should pbl be split up
@Controller
public class ExerciseApi {

    // ToDo: Should be some kind of ChannelApi
    private final ChannelRepository channelRepository;

    // ToDo: Should be some kind of AssessmentApi
    private final GradingCriterionRepository gradingCriterionRepository;

    // ToDo: Should be some kind of AssessmentApi
    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseService exerciseService;

    public ExerciseApi(AuthorizationCheckService authCheckService, ChannelRepository channelRepository, GradingCriterionRepository gradingCriterionRepository,
            ExerciseService exerciseService, ExampleSubmissionRepository exampleSubmissionRepository) {
        this.authCheckService = authCheckService;
        this.channelRepository = channelRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.exerciseService = exerciseService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
    }

    public boolean isStudentAllowedToSee(Exercise exercise) {
        return isStudentAllowedToSee(exercise, null);
    }

    public boolean isStudentAllowedToSee(Exercise exercise, User user) {
        if (exercise.isExamExercise()) {
            return false;
        }

        return authCheckService.isAllowedToSeeExercise(exercise, user);
    }

    public boolean isCorrectExerciseType(Exercise exercise, Class<? extends Exercise> expectedExerciseClass) {
        return expectedExerciseClass.isInstance(exercise);
    }

    public void setChannelName(Exercise exercise) {
        Channel channel = channelRepository.findChannelByExerciseId(exercise.getId());
        if (channel != null) {
            exercise.setChannelName(channel.getName());
        }
    }

    public void setGradingCriteria(Exercise exercise) {
        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
        exercise.setGradingCriteria(gradingCriteria);
    }

    public void checkExerciseIfStructuredGradingInstructionFeedbackUsed(Set<GradingCriterion> gradingCriteria, Exercise exercise) {
        exerciseService.checkExerciseIfStructuredGradingInstructionFeedbackUsed(gradingCriteria, exercise);
    }

    public void setExampleSubmissions(Exercise exercise) {
        Set<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllWithResultByExerciseId(exercise.getId());
        exercise.setExampleSubmissions(exampleSubmissions);
    }

    public void filterSensitiveData(Exercise exercise) {
        exercise.filterSensitiveInformation();
    }
}
