package de.tum.cit.aet.artemis.programming;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.core.connector.AeolusRequestMockProvider;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseService;
import de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService;
import de.tum.cit.aet.artemis.programming.service.localci.SharedQueueManagementService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

public abstract class AbstractProgrammingIntegrationLocalCILocalVCTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    // Config
    @Autowired
    @Qualifier("hazelcastInstance")
    protected HazelcastInstance hazelcastInstance;

    @Value("${artemis.user-management.internal-admin.username}")
    protected String localVCUsername;

    @Value("${artemis.user-management.internal-admin.password}")
    protected String localVCPassword;

    // Repositories
    @Autowired
    protected ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    protected StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    protected VcsAccessLogRepository vcsAccessLogRepository;

    // External Repositories

    // Services
    @Autowired
    protected AeolusRequestMockProvider aeolusRequestMockProvider;

    @Autowired
    protected ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Autowired
    protected ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    protected SharedQueueManagementService sharedQueueManagementService;

    @Autowired
    protected StaticCodeAnalysisService staticCodeAnalysisService;

    // External Services

    // Util Services
    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    // External Util Services
    @Autowired
    protected CompetencyUtilService competencyUtilService;

    @Autowired
    protected ExamUtilService examUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;
}
