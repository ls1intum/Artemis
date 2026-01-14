package de.tum.cit.aet.artemis.athena.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.athena.dto.ExerciseBaseDTO;
import de.tum.cit.aet.artemis.athena.dto.ModelingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ResponseMetaDTO;
import de.tum.cit.aet.artemis.athena.dto.SubmissionBaseDTO;
import de.tum.cit.aet.artemis.athena.dto.TextFeedbackDTO;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearnerProfileDTO;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Service for receiving feedback suggestions from the Athena service.
 * Assumes that submissions and already given feedback have already been sent to Athena or that the feedback is non-graded.
 */
@Lazy
@Service
@Profile(PROFILE_ATHENA)
public class AthenaFeedbackSuggestionsService {

    private static final Logger log = LoggerFactory.getLogger(AthenaFeedbackSuggestionsService.class);

    private final AthenaConnector<RequestDTO, ResponseDTOText> textAthenaConnector;

    private final AthenaConnector<RequestDTO, ResponseDTOProgramming> programmingAthenaConnector;

    private final AthenaConnector<RequestDTO, ResponseDTOModeling> modelingAthenaConnector;

    private final AthenaModuleService athenaModuleService;

    private final AthenaDTOConverterService athenaDTOConverterService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final ResultRepository resultRepository;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    @Value("${artemis.athena.allowed-feedback-requests:10}")
    private int allowedFeedbackRequests;

    /**
     * Create a new AthenaFeedbackSuggestionsService to receive feedback suggestions from the Athena service.
     *
     * @param athenaRestTemplate        REST template used for the communication with Athena
     * @param athenaModuleService       Athena module serviced used to determine the urls for different modules
     * @param athenaDTOConverterService Service to convert exercises and submissions to DTOs
     * @param llmTokenUsageService      Service to store the usage of LLM tokens
     * @param learnerProfileApi         API for learner profile operations
     * @param courseCompetencyApi       API for course competency operations
     */
    public AthenaFeedbackSuggestionsService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, AthenaModuleService athenaModuleService,
            AthenaDTOConverterService athenaDTOConverterService, LLMTokenUsageService llmTokenUsageService, ResultRepository resultRepository,
            Optional<LearnerProfileApi> learnerProfileApi, Optional<CourseCompetencyApi> courseCompetencyApi) {
        textAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOText.class);
        programmingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOProgramming.class);
        modelingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOModeling.class);
        this.athenaDTOConverterService = athenaDTOConverterService;
        this.athenaModuleService = athenaModuleService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.learnerProfileApi = learnerProfileApi;
        this.resultRepository = resultRepository;
        this.courseCompetencyApi = courseCompetencyApi;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record RequestDTO(@NonNull ExerciseBaseDTO exercise, @NonNull SubmissionBaseDTO submission, @Nullable LearnerProfileDTO learnerProfile, @NonNull boolean isGraded,
            @Nullable SubmissionBaseDTO latestSubmission, @Nullable List<CourseCompetencyDTO> competencies) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTOText(List<TextFeedbackDTO> data, ResponseMetaDTO meta) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTOProgramming(List<ProgrammingFeedbackDTO> data, ResponseMetaDTO meta) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTOModeling(List<ModelingFeedbackDTO> data, ResponseMetaDTO meta) {
    }

    private Submission getLatestSubmission(StudentParticipation participation) {
        var latestResult = resultRepository.findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(participation.getId()).orElse(null);
        Submission latestSubmission = null;
        if (latestResult != null) {
            latestSubmission = latestResult.getSubmission();
        }
        return latestSubmission;
    }

    /**
     * Extract the learner profile from a submission if it's a student participation.
     * This method handles the extraction of learner profile information from a submission,
     * with proper error handling and logging.
     *
     * @param submission the submission to extract the profile from
     * @return the learner profile or null if not available
     */
    private LearnerProfile extractLearnerProfile(Submission submission) {
        if (submission == null) {
            log.debug("Cannot extract learner profile: submission is null");
            return null;
        }

        if (!(submission.getParticipation() instanceof StudentParticipation studentParticipation)) {
            log.debug("Cannot extract learner profile: submission is not from a student participation");
            return null;
        }

        // Get the student from the participation
        var studentOpt = studentParticipation.getStudent();
        if (studentOpt.isEmpty()) {
            log.debug("Cannot extract learner profile: no student found in participation");
            return null;
        }

        var student = studentOpt.get();

        try {
            return learnerProfileApi.map(api -> api.getOrCreateLearnerProfile(student)).orElse(null);
        }
        catch (Exception e) {
            log.error("Error retrieving learner profile for student {}: {}", student.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Calls the remote Athena service to get feedback suggestions for a given submission.
     *
     * @param exercise   the {@link TextExercise} the suggestions are fetched for
     * @param submission the {@link TextSubmission} the suggestions are fetched for
     * @param isGraded   the {@link Boolean} should Athena generate grade suggestions or not
     * @return a list of feedback suggestions
     */
    public List<TextFeedbackDTO> getTextFeedbackSuggestions(TextExercise exercise, TextSubmission submission, boolean isGraded) throws NetworkingException {
        log.debug("Start Athena '{}' Feedback Suggestions Service for Exercise '{}' (#{}).", isGraded ? "Graded" : "Non Graded", exercise.getTitle(), exercise.getId());

        if (exercise.getFeedbackSuggestionModule() == null) {
            log.warn("Exercise '{}' (#{}) does not have a feedback suggestion module configured. Returning empty list.", exercise.getTitle(), exercise.getId());
            return List.of();
        }

        if (!Objects.equals(submission.getParticipation().getExercise().getId(), exercise.getId())) {
            log.error("Exercise id {} does not match submission's exercise id {}", exercise.getId(), submission.getParticipation().getExercise().getId());
            throw new ConflictException("Exercise id " + exercise.getId() + " does not match submission's exercise id " + submission.getParticipation().getExercise().getId(),
                    "Exercise", "exerciseIdDoesNotMatch");
        }

        Submission latestSubmission = getLatestSubmission((StudentParticipation) submission.getParticipation());
        SubmissionBaseDTO latestSubmissionDTO = latestSubmission != null ? athenaDTOConverterService.ofSubmission(exercise.getId(), latestSubmission) : null;
        List<CourseCompetencyDTO> competencies = courseCompetencyApi.map(api -> api.findAllByExerciseId(exercise.getId()).stream().map(CourseCompetencyDTO::of).toList())
                .orElse(null);
        final RequestDTO request = new RequestDTO(athenaDTOConverterService.ofExercise(exercise), athenaDTOConverterService.ofSubmission(exercise.getId(), submission),
                LearnerProfileDTO.of(extractLearnerProfile(submission)), isGraded, latestSubmissionDTO, competencies);
        ResponseDTOText response = textAthenaConnector.invokeWithRetry(athenaModuleService.getAthenaModuleUrl(exercise) + "/feedback_suggestions", request, 0);
        log.info("Athena responded to '{}' feedback suggestions request: {}", isGraded ? "Graded" : "Non Graded", response.data);
        storeTokenUsage(exercise, submission, response.meta, !isGraded);
        return response.data.stream().toList();
    }

    /**
     * Calls the remote Athena service to get feedback suggestions for a given programming submission.
     *
     * @param exercise   the {@link ProgrammingExercise} the suggestions are fetched for
     * @param submission the {@link ProgrammingSubmission} the suggestions are fetched for
     * @param isGraded   the {@link Boolean} should Athena generate grade suggestions or not
     * @return a list of feedback suggestions
     */
    public List<ProgrammingFeedbackDTO> getProgrammingFeedbackSuggestions(ProgrammingExercise exercise, ProgrammingSubmission submission, boolean isGraded)
            throws NetworkingException {
        log.debug("Start Athena '{}' Feedback Suggestions Service for Exercise '{}' (#{}).", isGraded ? "Graded" : "Non Graded", exercise.getTitle(), exercise.getId());

        if (exercise.getFeedbackSuggestionModule() == null) {
            log.warn("Exercise '{}' (#{}) does not have a feedback suggestion module configured. Returning empty list.", exercise.getTitle(), exercise.getId());
            return List.of();
        }

        final RequestDTO request = new RequestDTO(athenaDTOConverterService.ofExercise(exercise), athenaDTOConverterService.ofSubmission(exercise.getId(), submission), null,
                isGraded, null, null);
        ResponseDTOProgramming response = programmingAthenaConnector.invokeWithRetry(athenaModuleService.getAthenaModuleUrl(exercise) + "/feedback_suggestions", request, 0);
        log.info("Athena responded to '{}' feedback suggestions request: {}", isGraded ? "Graded" : "Non Graded", response.data);
        storeTokenUsage(exercise, submission, response.meta, !isGraded);
        return response.data.stream().toList();
    }

    /**
     * Retrieve feedback suggestions for a given modeling exercise submission from Athena
     *
     * @param exercise   the {@link ModelingExercise} the suggestions are fetched for
     * @param submission the {@link ModelingSubmission} the suggestions are fetched for
     * @param isGraded   the {@link Boolean} should Athena generate grade suggestions or not
     * @return a list of feedback suggestions generated by Athena
     */
    public List<ModelingFeedbackDTO> getModelingFeedbackSuggestions(ModelingExercise exercise, ModelingSubmission submission, boolean isGraded) throws NetworkingException {
        log.debug("Start Athena '{}' Feedback Suggestions Service for Modeling Exercise '{}' (#{}).", isGraded ? "Graded" : "Non Graded", exercise.getTitle(), exercise.getId());

        if (exercise.getFeedbackSuggestionModule() == null) {
            log.warn("Exercise '{}' (#{}) does not have a feedback suggestion module configured. Returning empty list.", exercise.getTitle(), exercise.getId());
            return List.of();
        }

        if (!Objects.equals(submission.getParticipation().getExercise().getId(), exercise.getId())) {
            throw new ConflictException("Exercise id " + exercise.getId() + " does not match submission's exercise id " + submission.getParticipation().getExercise().getId(),
                    "Exercise", "exerciseIdDoesNotMatch");
        }

        final RequestDTO request = new RequestDTO(athenaDTOConverterService.ofExercise(exercise), athenaDTOConverterService.ofSubmission(exercise.getId(), submission), null,
                isGraded, null, null);
        ResponseDTOModeling response = modelingAthenaConnector.invokeWithRetry(athenaModuleService.getAthenaModuleUrl(exercise) + "/feedback_suggestions", request, 0);
        log.info("Athena responded to '{}' feedback suggestions request: {}", isGraded ? "Graded" : "Non Graded", response.data);
        storeTokenUsage(exercise, submission, response.meta, !isGraded);
        return response.data;
    }

    /**
     * Store the usage of LLM tokens for a given submission
     *
     * @param exercise              the exercise the submission belongs to
     * @param submission            the submission for which the tokens were used
     * @param meta                  the meta information of the response from Athena
     * @param isPreliminaryFeedback whether the feedback is preliminary or not
     */
    private void storeTokenUsage(Exercise exercise, Submission submission, ResponseMetaDTO meta, Boolean isPreliminaryFeedback) {
        if (meta == null) {
            return;
        }
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();
        Long userId;
        if (submission.getParticipation() instanceof StudentParticipation studentParticipation) {
            userId = studentParticipation.getStudent().map(User::getId).orElse(null);
        }
        else {
            userId = null;
        }
        List<LLMRequest> llmRequests = meta.llmRequests();
        if (llmRequests == null) {
            return;
        }

        llmTokenUsageService.saveLLMTokenUsage(llmRequests, LLMServiceType.ATHENA,
                (llmTokenUsageBuilder -> llmTokenUsageBuilder.withCourse(courseId).withExercise(exercise.getId()).withUser(userId)));
    }

    /**
     * Checks if the number of Athena results for the given participation exceeds
     * the allowed threshold and throws an exception if the limit is reached.
     *
     * @param participation the student participation to check
     * @throws BadRequestAlertException if the maximum number of Athena feedback requests is exceeded
     */
    public void checkRateLimitOrThrow(StudentParticipation participation) {
        List<Result> athenaResults = participation.getSubmissions().stream()
                .flatMap(submission -> submission.getResults().stream().filter(result -> result != null && result.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA)).toList();

        long countOfSuccessfulRequests = athenaResults.stream().filter(result -> result.isSuccessful() == Boolean.TRUE).count();

        if (countOfSuccessfulRequests >= this.allowedFeedbackRequests) {
            throw new BadRequestAlertException("Maximum number of AI feedback requests reached.", "participation", "maxAthenaResultsReached", true);
        }
    }

    /**
     * Ensures that the submission does not already have an Athena-generated result.
     * Throws an exception if Athena result already exists.
     *
     * @param submission the student's submission to validate
     * @throws BadRequestAlertException if an Athena result is already present for the submission
     */
    public void checkLatestSubmissionHasNoAthenaResultOrThrow(Submission submission) {
        Result latestResult = submission.getLatestResult();

        if (latestResult != null && latestResult.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA) {
            log.debug("Submission ID: {} already has an Athena result. Skipping feedback generation.", submission.getId());
            throw new BadRequestAlertException("Submission already has an Athena result", "submission", "submissionAlreadyHasAthenaResult", true);
        }
    }
}
