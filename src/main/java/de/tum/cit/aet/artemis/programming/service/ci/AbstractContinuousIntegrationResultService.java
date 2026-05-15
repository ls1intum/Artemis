package de.tum.cit.aet.artemis.programming.service.ci;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.dto.BuildJobInterface;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.TestCaseBase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;

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

            job.failedTests().forEach(failedTest -> result
                    .addFeedback(feedbackCreationService.createFeedbackFromTestCase(failedTest.name(), failedTest.testMessages(), false, programmingExercise, activeTestCases)));
            result.setTestCaseCount(result.getTestCaseCount() + job.failedTests().size());

            for (final var successfulTest : job.successfulTests()) {
                result.addFeedback(
                        feedbackCreationService.createFeedbackFromTestCase(successfulTest.name(), successfulTest.testMessages(), true, programmingExercise, activeTestCases));
            }

            result.setTestCaseCount(result.getTestCaseCount() + job.successfulTests().size());
            result.setPassedTestCaseCount(result.getPassedTestCaseCount() + job.successfulTests().size());
        });
    }

    private void addStaticCodeAnalysisFeedbackToResult(Result result, BuildResultNotification buildResult, ProgrammingExercise programmingExercise) {
        final var staticCodeAnalysisReports = buildResult.staticCodeAnalysisReports();
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && staticCodeAnalysisReports != null && !staticCodeAnalysisReports.isEmpty()) {
            List<Feedback> scaFeedbackList = feedbackCreationService.createFeedbackFromStaticCodeAnalysisReports(staticCodeAnalysisReports);
            result.addFeedbacks(scaFeedbackList);
            result.setCodeIssueCount(scaFeedbackList.size());
        }
    }
}
