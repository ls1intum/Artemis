package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequest;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInput;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ProblemStatementRenderingService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ProblemStatementRenderingResource {

    private static final Logger log = LoggerFactory.getLogger(ProblemStatementRenderingResource.class);

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ExamDateApi> examDateApi;

    private final ProblemStatementRenderingService renderingService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    public ProblemStatementRenderingResource(ExerciseRepository exerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            Optional<ExamDateApi> examDateApi, ProblemStatementRenderingService renderingService, StudentParticipationRepository studentParticipationRepository,
            ResultRepository resultRepository) {
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.examDateApi = examDateApi;
        this.renderingService = renderingService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * GET exercises/{exerciseId}/problem-statement/rendered : Returns the pre-rendered HTML of an exercise's problem statement.
     *
     * @param exerciseId    the id of the exercise
     * @param selfContained if true, PlantUML diagrams are inlined as SVG (default: false)
     * @param interactive   if true and selfContained is true, includes vanilla JS for task feedback popups (default: true)
     * @return the rendered problem statement DTO
     */
    @GetMapping(value = "exercises/{exerciseId}/problem-statement/rendered", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<RenderedProblemStatementDTO> getRenderedProblemStatement(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean selfContained,
            @RequestParam(defaultValue = "true") boolean interactive) {

        log.debug("REST request to get rendered problem statement for Exercise : {}", exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        // Authorization: mirror ExerciseResource.getExercise()
        if (exercise.isExamExercise()) {
            Exam exam = exercise.getExerciseGroup().getExam();
            ExamDateApi api = examDateApi.orElseThrow(() -> new ExamApiNotPresentException(ExamDateApi.class));
            if (authCheckService.isAtLeastEditorForExercise(exercise, user)) {
                // editors, instructors, and admins always allowed
            }
            else if (authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                ZonedDateTime latestIndividualExamEndDate = api.getLatestIndividualExamEndDate(exam);
                if (latestIndividualExamEndDate == null || latestIndividualExamEndDate.isAfter(ZonedDateTime.now())) {
                    throw new AccessForbiddenException();
                }
            }
            else {
                // Students should never access exam exercises through this endpoint
                throw new AccessForbiddenException();
            }
        }
        else {
            if (!authCheckService.isAllowedToSeeCourseExercise(exercise, user)) {
                throw new AccessForbiddenException();
            }
        }

        var feedbackData = fetchFeedbackData(exercise, user);
        String langKey = user.getLangKey();
        Locale locale = langKey == null || langKey.isBlank() ? Locale.ENGLISH : Locale.forLanguageTag(langKey);
        RenderedProblemStatementDTO result = renderingService.render(exercise, selfContained, interactive, feedbackData.testResults(), feedbackData.resultSummary(), locale);

        return ResponseEntity.ok().eTag("\"" + result.contentHash() + "\"").cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate()).body(result);
    }

    /**
     * POST problem-statement/render : Stateless rendering of a problem statement.
     * The client sends markdown + test data, the server returns rendered HTML. Zero database access.
     *
     * @param renderRequest the render request containing markdown, test results, and configuration
     * @return the rendered problem statement DTO
     */
    @PostMapping(value = "problem-statement/render", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<RenderedProblemStatementDTO> renderProblemStatement(@Valid @RequestBody ProblemStatementRenderRequest renderRequest) {

        log.debug("REST request to render problem statement (stateless)");

        // Convert client-provided test results to internal format
        Map<Long, ProblemStatementRenderingService.TestFeedbackDetail> testResults = null;
        if (renderRequest.testResults() != null && !renderRequest.testResults().isEmpty()) {
            testResults = new HashMap<>();
            for (TestFeedbackInput input : renderRequest.testResults()) {
                testResults.put(input.testId(),
                        new ProblemStatementRenderingService.TestFeedbackDetail(input.testId(), input.testName(), input.passed(), input.message(), input.credits()));
            }
        }

        // Convert client-provided result summary
        ProblemStatementRenderingService.ResultSummary resultSummary = null;
        if (renderRequest.resultSummary() != null) {
            var rs = renderRequest.resultSummary();
            resultSummary = new ProblemStatementRenderingService.ResultSummary(rs.score(), rs.maxPoints(), rs.bonusPoints(), rs.commitHash(), rs.submissionDate(),
                    rs.assessmentType());
        }

        String lang = renderRequest.locale() != null ? renderRequest.locale() : "en";
        Locale locale = Locale.forLanguageTag(lang);

        RenderedProblemStatementDTO result = renderingService.renderStateless(renderRequest.markdown(), renderRequest.selfContained(), renderRequest.interactive(), testResults,
                resultSummary, locale);

        return ResponseEntity.ok().eTag("\"" + result.contentHash() + "\"").body(result);
    }

    private record FeedbackData(@Nullable Map<Long, ProblemStatementRenderingService.TestFeedbackDetail> testResults,
            ProblemStatementRenderingService.@Nullable ResultSummary resultSummary) {
    }

    /**
     * Fetches the current user's latest test feedback and result summary for the given exercise.
     */
    private FeedbackData fetchFeedbackData(Exercise exercise, User user) {
        var participation = studentParticipationRepository.findByExerciseIdAndStudentIdAndTestRunWithLatestResult(exercise.getId(), user.getId(), false);
        if (participation.isEmpty()) {
            return new FeedbackData(null, null);
        }

        long participationId = participation.get().getId();
        Optional<Result> latestResult = resultRepository.findFirstWithFeedbacksTestCasesByParticipationIdOrderByCompletionDateDesc(participationId);
        if (latestResult.isEmpty()) {
            return new FeedbackData(null, null);
        }

        Result resultObj = latestResult.get();

        // Build per-test feedback map
        Map<Long, ProblemStatementRenderingService.TestFeedbackDetail> testResults = new HashMap<>();
        for (Feedback feedback : resultObj.getFeedbacks()) {
            if (feedback.getTestCase() != null && feedback.getTestCase().getId() != null) {
                var testCase = feedback.getTestCase();
                String testName = testCase.getTestName() != null ? testCase.getTestName() : "Test " + testCase.getId();
                boolean passed = Boolean.TRUE.equals(feedback.isPositive());
                String message = feedback.getDetailText();
                Double credits = feedback.getCredits();
                testResults.put(testCase.getId(), new ProblemStatementRenderingService.TestFeedbackDetail(testCase.getId(), testName, passed, message, credits));
            }
        }

        // Build result-level summary
        String commitHash = null;
        if (resultObj.getSubmission() instanceof ProgrammingSubmission ps) {
            commitHash = ps.getCommitHash();
        }
        String submissionDate = null;
        if (resultObj.getSubmission() != null && resultObj.getSubmission().getSubmissionDate() != null) {
            submissionDate = resultObj.getSubmission().getSubmissionDate().toString();
        }
        String assessmentType = resultObj.getAssessmentType() != null ? resultObj.getAssessmentType().name() : null;

        var resultSummary = new ProblemStatementRenderingService.ResultSummary(resultObj.getScore(), exercise.getMaxPoints(), exercise.getBonusPoints(), commitHash, submissionDate,
                assessmentType);

        return new FeedbackData(testResults, resultSummary);
    }
}
