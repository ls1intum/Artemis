package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.BuildLogEntryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for both scenarios:
 * 1) Bamboo + Bitbucket
 * 2) Jenkins + Gitlab
 */
@Service
public class ProgrammingExerciseResultTestService {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private BuildLogEntryRepository buildLogEntryRepository;

    @Autowired
    private ProgrammingExerciseGradingService gradingService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private ProgrammingExerciseTestCaseService programmingExerciseTestCaseService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExercise programmingExerciseWithStaticCodeAnalysis;

    private SolutionProgrammingExerciseParticipation solutionParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipationStaticCodeAnalysis;

    public void setup() {
        database.addUsers(10, 2, 2);
        setupForProgrammingLanguage(ProgrammingLanguage.JAVA);
    }

    public void setupForProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
        Course course = database.addCourseWithOneProgrammingExercise(false, programmingLanguage);
        programmingExercise = programmingExerciseRepository.findAll().get(0);
        programmingExerciseWithStaticCodeAnalysis = database.addProgrammingExerciseToCourse(course, true, programmingLanguage);
        staticCodeAnalysisService.createDefaultCategories(programmingExerciseWithStaticCodeAnalysis);
        // This is done to avoid an unproxy issue in the processNewResult method of the ResultService.
        solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).get();
        programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        programmingExerciseStudentParticipationStaticCodeAnalysis = database.addStudentParticipationForProgrammingExercise(programmingExerciseWithStaticCodeAnalysis, "student1");
    }

    public void tearDown() {
        database.resetDatabase();
    }

    // Test
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(Object resultNotification) {
        database.createProgrammingSubmission(programmingExerciseStudentParticipation, false);

        Set<ProgrammingExerciseTestCase> expectedTestCases = new HashSet<>();
        expectedTestCases.add(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test1").active(true).weight(1.0).id(1L).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(TestCaseVisibility.ALWAYS));
        expectedTestCases.add(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test2").active(true).weight(1.0).id(2L).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(TestCaseVisibility.ALWAYS));
        expectedTestCases.add(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test4").active(true).weight(1.0).id(3L).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(TestCaseVisibility.ALWAYS));

        final var optionalResult = gradingService.processNewProgrammingExerciseResult(solutionParticipation, resultNotification);

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseService.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).usingElementComparatorIgnoringFields("exercise", "id").isEqualTo(expectedTestCases);
        assertThat(optionalResult).isPresent();
        assertThat(optionalResult.get().getScore()).isEqualTo(100L);
    }

    // Test
    public void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(Object resultNotification, ProgrammingLanguage programmingLanguage) {
        final var optionalResult = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipationStaticCodeAnalysis, resultNotification);
        final var savedResult = resultService.findOneWithEagerSubmissionAndFeedback(optionalResult.get().getId());

        // Create comparator to explicitly compare feedback attributes (equals only compares id)
        Comparator<? super Feedback> scaFeedbackComparator = (Comparator<Feedback>) (fb1, fb2) -> {
            if (Objects.equals(fb1.getDetailText(), fb2.getDetailText()) && Objects.equals(fb1.getText(), fb2.getText())
                    && Objects.equals(fb1.getReference(), fb2.getReference())) {
                return 0;
            }
            else {
                return 1;
            }
        };

        assertThat(optionalResult).isPresent();
        var result = optionalResult.get();
        assertThat(result.getFeedbacks()).usingElementComparator(scaFeedbackComparator).containsAll(savedResult.getFeedbacks());
        assertThat(result.getFeedbacks().stream().filter(Feedback::isStaticCodeAnalysisFeedback).count())
                .isEqualTo(StaticCodeAnalysisTool.getToolsForProgrammingLanguage(programmingLanguage).size());
    }

    // Test
    public void shouldStoreBuildLogsForSubmission(Object resultNotification) {
        final var optionalResult = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);

        var submission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(programmingExerciseStudentParticipation.getId());
        var submissionWithLogs = programmingSubmissionRepository.findWithEagerBuildLogEntriesById(submission.get().getId());
        var expectedNoOfLogs = getNumberOfBuildLogs(resultNotification);
        assertThat(((ProgrammingSubmission) optionalResult.get().getSubmission()).getBuildLogEntries()).hasSize(expectedNoOfLogs);
        assertThat(submissionWithLogs.get().getBuildLogEntries()).hasSize(expectedNoOfLogs);
    }

    public void shouldSaveBuildLogsInBuildLogRepository(Object resultNotification) {
        buildLogEntryRepository.deleteAll();
        gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);

        var savedBuildLogs = buildLogEntryRepository.findAll();
        var expectedBuildLogs = getNumberOfBuildLogs(resultNotification);

        assertThat(savedBuildLogs.size()).isEqualTo(expectedBuildLogs);
        savedBuildLogs.forEach(buildLogEntry -> {
            assertThat(buildLogEntry.getProgrammingSubmission().getParticipation().getId()).isEqualTo(programmingExerciseStudentParticipation.getId());
        });
    }

    // Test
    public void shouldGenerateNewManualResultIfManualAssessmentExists(Object resultNotification) {
        var programmingSubmission = database.createProgrammingSubmission(programmingExerciseStudentParticipation, false);
        programmingSubmission = database.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, "student1", "tutor1",
                AssessmentType.SEMI_AUTOMATIC, true);

        List<Feedback> feedback = ModelFactory.generateManualFeedback();
        feedback = database.feedbackRepo.saveAll(feedback);
        programmingSubmission.getFirstResult().addFeedbacks(feedback);
        database.resultRepo.save(programmingSubmission.getFirstResult());

        final var optionalResult = gradingService.processNewProgrammingExerciseResult(programmingExerciseStudentParticipation, resultNotification);

        assertThat(optionalResult).isPresent();

        var result = optionalResult.get();

        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(result.getFeedbacks()).hasSize(6);
        assertThat(result.getFeedbacks().stream().filter((fb) -> fb.getType() == FeedbackType.AUTOMATIC).count()).isEqualTo(3);
    }

    private int getNumberOfBuildLogs(Object resultNotification) {
        if (resultNotification.getClass() == BambooBuildResultNotificationDTO.class) {
            return ((BambooBuildResultNotificationDTO) resultNotification).getBuild().getJobs().iterator().next().getLogs().size();
        }
        throw new UnsupportedOperationException("Build logs are only part of the Bamboo notification");
    }
}
