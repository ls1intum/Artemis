package de.tum.cit.aet.artemis.localci.service.ci;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.localci.dto.BuildJobInterface;
import de.tum.cit.aet.artemis.localci.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.TestCaseBase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

public abstract class AbstractContinuousIntegrationResultService implements ContinuousIntegrationResultService {

    private static final Logger log = LoggerFactory.getLogger(AbstractContinuousIntegrationResultService.class);

    protected final ProgrammingExerciseTestCaseRepository testCaseRepository;

    protected final ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    protected final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    protected AbstractContinuousIntegrationResultService(ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.testCaseRepository = testCaseRepository;
        this.feedbackCreationService = feedbackCreationService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    @Override
    public Result createResultFromBuildResult(BuildResultNotification buildResult, ProgrammingExerciseParticipation participation) {
        ProgrammingExercise exercise = participation.getProgrammingExercise();

        final var result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setSuccessful(buildResult.isBuildSuccessful());
        result.setCompletionDate(buildResult.buildRunDate());
        // this only sets the score to a temporary value, the real score is calculated in the grading service
        result.setScore(buildResult.buildScore(), exercise.getCourseViaExerciseGroupOrCourseMember());

        addFeedbackToResult(result, buildResult, exercise);
        return result;
    }

    /**
     * Converts build result details into feedback and stores it in the result object
     *
     * @param result              the result for which the feedback should be added
     * @param buildResult         The build result
     * @param programmingExercise the programming exercise related to the result
     *
     */
    private void addFeedbackToResult(Result result, BuildResultNotification buildResult, ProgrammingExercise programmingExercise) {
        final var jobs = buildResult.jobs();

        // 1) add feedback for failed and passed test cases
        addTestCaseFeedbacksToResult(result, jobs, programmingExercise);

        // 2) process static code analysis feedback
        addStaticCodeAnalysisFeedbackToResult(result, buildResult, programmingExercise);
    }

    private void addTestCaseFeedbacksToResult(Result result, List<? extends BuildJobInterface> jobs, ProgrammingExercise programmingExercise) {
        var activeTestCases = testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true);

        if (log.isDebugEnabled()) {
            log.debug("Building result feedbacks for exercise {}: {} active test cases in DB (names: {})", programmingExercise.getId(), activeTestCases.size(),
                    activeTestCases.stream().map(ProgrammingExerciseTestCase::getTestName).sorted().toList());
        }

        jobs.forEach(job -> {
            if (log.isDebugEnabled()) {
                var failedTestNames = job.failedTests().stream().map(TestCaseBase::name).sorted().toList();
                var successfulTestNames = job.successfulTests().stream().map(TestCaseBase::name).sorted().toList();
                log.debug("Build job for exercise {}: {} failed tests {}, {} successful tests {}", programmingExercise.getId(), failedTestNames.size(), failedTestNames,
                        successfulTestNames.size(), successfulTestNames);
                job.failedTests().forEach(failedTest -> log.debug("Build job for exercise {}: failed test '{}' messages: {}", programmingExercise.getId(), failedTest.name(),
                        failedTest.testMessages()));
            }

            job.failedTests().forEach(failedTest -> feedbackCreationService
                    .createFeedbackFromTestCase(failedTest.name(), failedTest.testMessages(), false, programmingExercise, activeTestCases).ifPresent(result::addTestCaseFeedback));
            result.setTestCaseCount(result.getTestCaseCount() + job.failedTests().size());

            for (final var successfulTest : job.successfulTests()) {
                feedbackCreationService.createFeedbackFromTestCase(successfulTest.name(), successfulTest.testMessages(), true, programmingExercise, activeTestCases)
                        .ifPresent(result::addTestCaseFeedback);
            }

            result.setTestCaseCount(result.getTestCaseCount() + job.successfulTests().size());
            result.setPassedTestCaseCount(result.getPassedTestCaseCount() + job.successfulTests().size());
        });

        if (result.getTestCaseCount() > 0 && result.getTestCaseFeedbacks().isEmpty()) {
            // Not a failed build: the tests ran, but Artemis cannot attribute any of them. This is what a failed or
            // missing solution build looks like from here, because the solution result is what registers the test
            // cases - without them no feedback can be stored and nothing counts towards the score. Worth a warning
            // rather than a silent empty result, since only re-running the solution build fixes it.
            log.warn("The build of exercise {} reported {} test(s), but none of them matches one of its {} active test case(s). The solution build has probably not registered "
                    + "the test cases (yet).", programmingExercise.getId(), result.getTestCaseCount(), activeTestCases.size());
        }
    }

    private void addStaticCodeAnalysisFeedbackToResult(Result result, BuildResultNotification buildResult, ProgrammingExercise programmingExercise) {
        final var staticCodeAnalysisReports = buildResult.staticCodeAnalysisReports();
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && staticCodeAnalysisReports != null && !staticCodeAnalysisReports.isEmpty()) {
            List<ScaFeedback> scaFeedbackList = feedbackCreationService.createFeedbackFromStaticCodeAnalysisReports(staticCodeAnalysisReports);
            scaFeedbackList.forEach(result::addScaFeedback);
            result.setCodeIssueCount(scaFeedbackList.size());
        }
    }
}
