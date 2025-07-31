package de.tum.cit.aet.artemis.programming;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.repository.settings.IdeRepository;
import de.tum.cit.aet.artemis.programming.repository.settings.UserIdeMappingRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public abstract class AbstractProgrammingIntegrationIndependentTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    protected AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    protected IdeRepository ideRepository;

    @Autowired
    protected ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    protected ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    protected ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    protected ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    protected StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    protected UserIdeMappingRepository userIdeMappingRepository;

    // External Repositories
    @Autowired
    protected ComplaintRepository complaintRepo;

    @Autowired
    protected ExamTestRepository examRepository;

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

    @Autowired
    protected LongFeedbackTextRepository longFeedbackTextRepository;

    // Services
    @Autowired
    protected AuxiliaryRepositoryService auxiliaryRepositoryService;

    @Autowired
    protected BuildLogEntryService buildLogEntryService;

    @Autowired
    protected ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Autowired
    protected ProgrammingExerciseGradingService gradingService;

    @Autowired
    protected ProgrammingExerciseIntegrationTestService programmingExerciseIntegrationTestService;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

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

    @Autowired
    protected StudentExamService studentExamService;

    @Autowired
    protected ProgrammingExerciseService programmingExerciseService;

}
