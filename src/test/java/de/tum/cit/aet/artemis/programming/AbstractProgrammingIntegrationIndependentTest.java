package de.tum.cit.aet.artemis.programming;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.CodeHintRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.CoverageFileReportRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.CoverageReportRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ExerciseHintActivationRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ExerciseHintRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.TestwiseCoverageReportEntryRepository;
import de.tum.cit.aet.artemis.programming.repository.settings.IdeRepository;
import de.tum.cit.aet.artemis.programming.repository.settings.UserIdeMappingRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseRepositoryService;
import de.tum.cit.aet.artemis.programming.service.hestia.CodeHintService;
import de.tum.cit.aet.artemis.programming.service.hestia.ExerciseHintService;
import de.tum.cit.aet.artemis.programming.service.hestia.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.GitUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public abstract class AbstractProgrammingIntegrationIndependentTest extends AbstractSpringIntegrationIndependentTest {

    // Repositories
    @Autowired
    protected AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    protected CodeHintRepository codeHintRepository;

    @Autowired
    protected CoverageFileReportRepository coverageFileReportRepository;

    @Autowired
    protected CoverageReportRepository coverageReportRepository;

    @Autowired
    protected ExerciseHintActivationRepository exerciseHintActivationRepository;

    @Autowired
    protected ExerciseHintRepository exerciseHintRepository;

    @Autowired
    protected IdeRepository ideRepository;

    @Autowired
    protected ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    protected ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository;

    @Autowired
    protected ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    protected ProgrammingExerciseTaskRepository taskRepository;

    @Autowired
    protected ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    protected ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    protected SolutionProgrammingExerciseParticipationRepository solutionEntryRepository;

    @Autowired
    protected StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    protected TestwiseCoverageReportEntryRepository testwiseCoverageReportEntryRepository;

    @Autowired
    protected UserIdeMappingRepository userIdeMappingRepository;

    // External Repositories
    @Autowired
    protected ComplaintRepository complaintRepo;

    @Autowired
    protected ExamRepository examRepository;

    @Autowired
    protected ExerciseTestRepository exerciseRepository;

    @Autowired
    protected ParticipationTestRepository participationRepository;

    @Autowired
    protected StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    protected SubmissionTestRepository submissionRepository;

    @Autowired
    protected UserTestRepository userRepository;

    // Services
    @Autowired
    protected AuxiliaryRepositoryService auxiliaryRepositoryService;

    @Autowired
    protected BuildLogEntryService buildLogEntryService;

    @Autowired
    protected CodeHintService codeHintService;

    @Autowired
    protected ExerciseHintService exerciseHintService;

    @Autowired
    protected GitUtilService gitUtilService;

    @Autowired
    protected ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Autowired
    protected ProgrammingExerciseGradingService gradingService;

    @Autowired
    protected ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    @Autowired
    protected ProgrammingExerciseTaskService programmingExerciseTaskService;

    // External Services

    // Util Services
    @Autowired
    protected ProgrammingExerciseIntegrationTestService programmingExerciseIntegrationTestService;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    // External Util Services
    @Autowired
    protected ComplaintUtilService complaintUtilService;

    @Autowired
    protected CourseUtilService courseUtilService;

    @Autowired
    protected ExamUtilService examUtilService;

    @Autowired
    protected ExerciseUtilService exerciseUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    protected UserUtilService userUtilService;

}
