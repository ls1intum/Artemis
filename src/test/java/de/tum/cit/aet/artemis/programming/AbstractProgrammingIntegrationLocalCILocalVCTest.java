package de.tum.cit.aet.artemis.programming;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.buildagent.service.SharedQueueProcessingService;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.connector.AeolusRequestMockProvider;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseIntegrationTestService;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportBasicService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseService;
import de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.SharedQueueManagementService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

public abstract class AbstractProgrammingIntegrationLocalCILocalVCTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    // Config
    @Value("${artemis.user-management.internal-admin.username}")
    protected String localVCUsername;

    @Value("${artemis.user-management.internal-admin.password}")
    protected String localVCPassword;

    @Autowired
    protected ObjectMapper objectMapper;

    // Repositories
    @Autowired
    protected ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    protected StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    protected VcsAccessLogRepository vcsAccessLogRepository;

    @Autowired
    protected AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    // External Repositories
    @Autowired
    protected ExamTestRepository examRepository;

    @Autowired
    protected StudentExamTestRepository studentExamRepository;

    @Autowired
    protected StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    protected PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    protected PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    protected PostTestRepository postRepository;

    // Services
    @Autowired
    protected BuildLogEntryService buildLogEntryService;

    @Autowired
    protected ProgrammingExerciseIntegrationTestService programmingExerciseIntegrationTestService;

    @Autowired
    protected AeolusRequestMockProvider aeolusRequestMockProvider;

    @Autowired
    protected AeolusTemplateService aeolusTemplateService;

    @Autowired
    protected BuildScriptProviderService buildScriptProviderService;

    @Autowired
    protected ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Autowired
    protected ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    @Autowired
    protected ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    protected SharedQueueManagementService sharedQueueManagementService;

    @Autowired
    protected SharedQueueProcessingService sharedQueueProcessingService;

    @Autowired
    protected StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    protected DistributedDataAccessService distributedDataAccessService;
    // External Services

    // Util Services
    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    // External Util Services
    @Autowired
    protected CompetencyUtilService competencyUtilService;

    @Autowired
    protected ExamUtilService examUtilService;

    @Autowired
    protected ExerciseIntegrationTestService exerciseIntegrationTestService;

    @Autowired
    protected PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    protected TextExerciseUtilService textExerciseUtilService;

    @Autowired
    protected ProgrammingExerciseTestService programmingExerciseTestService;
}
