package de.tum.cit.aet.artemis.programming;

import java.net.URL;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageReceiveService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.repository.BuildLogStatisticsEntryRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.gitlabci.GitLabCIResultService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationGitlabCIGitlabSamlTest;

public abstract class AbstractProgrammingIntegrationGitlabCIGitlabSamlTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

    // Config

    @Value("${artemis.version-control.url}")
    protected URL gitlabServerUrl;

    // Repositories

    @Autowired
    protected BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    @Autowired
    protected BuildPlanRepository buildPlanRepository;

    @Autowired
    protected ParticipationTestRepository participationRepository;

    @Autowired
    protected ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    protected ProgrammingExerciseTestCaseTestRepository programmingExerciseTestCaseRepository;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

    // External Repositories

    @Autowired
    protected ExamRepository examRepository;

    @Autowired
    protected StudentExamTestRepository studentExamRepository;

    // Services

    @Autowired
    protected GitLabCIResultService gitLabCIResultService;

    // External Services

    @Autowired
    protected InstanceMessageReceiveService instanceMessageReceiveService;

    // Util Services

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    // External Util Services

    @Autowired
    protected ExamUtilService examUtilService;

    @Autowired
    protected ExerciseUtilService exerciseUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    protected UserUtilService userUtilService;

}
