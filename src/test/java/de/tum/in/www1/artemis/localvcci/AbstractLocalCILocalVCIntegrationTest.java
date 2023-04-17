package de.tum.in.www1.artemis.localvcci;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseGroupRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;

public class AbstractLocalCILocalVCIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    protected static final String TEST_PREFIX = "localvclocalciintegration";

    @Value("${artemis.version-control.url}")
    protected String localVCSBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    protected Path localVCSBasePath;

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    protected ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    protected TeamRepository teamRepository;

    @Autowired
    protected ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    protected ExamRepository examRepository;

    @Autowired
    protected StudentExamRepository studentExamRepository;

    @Autowired
    protected TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    protected SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @LocalServerPort
    protected int port;

    protected static final String DUMMY_COMMIT_HASH = "1234567890abcdef";

    // The error messages returned by JGit contain these Strings that correspond to the HTTP status codes.
    protected static final String NOT_FOUND = "not found";

    protected static final String NOT_AUTHORIZED = "not authorized";

    protected static final String INTERNAL_SERVER_ERROR = "500";

    protected static final String FORBIDDEN = "not permitted";

    protected Course course;

    protected ProgrammingExercise programmingExercise;

    protected ProgrammingExerciseStudentParticipation studentParticipation;

    protected ProgrammingExerciseStudentParticipation teachingAssistantParticipation;

    protected ProgrammingExerciseStudentParticipation instructorParticipation;

    protected TemplateProgrammingExerciseParticipation templateParticipation;

    protected SolutionProgrammingExerciseParticipation solutionParticipation;

    protected String student1Login;

    protected User student1;

    protected String student2Login;

    protected String tutor1Login;

    protected String instructor1Login;

    protected User instructor1;

    protected String projectKey1;

    protected String assignmentRepositorySlug;

    protected String templateRepositorySlug;

    protected String solutionRepositorySlug;

    @BeforeEach
    void initUsersAndExercise() throws IOException {
        // The port cannot be injected into the LocalVCLocalCITestService because {local.server.port} is not available when the class is instantiated.
        // Thus, "inject" the port from here.
        localVCLocalCITestService.setPort(port);

        List<User> users = database.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        student1Login = TEST_PREFIX + "student1";
        student1 = users.stream().filter(user -> student1Login.equals(user.getLogin())).findFirst().orElseThrow();
        student2Login = TEST_PREFIX + "student2";
        tutor1Login = TEST_PREFIX + "tutor1";
        instructor1Login = TEST_PREFIX + "instructor1";
        instructor1 = users.stream().filter(user -> instructor1Login.equals(user.getLogin())).findFirst().orElseThrow();

        // Set the Authentication object for student1 in the SecurityContextHolder.
        // This is necessary because the "database.addStudentParticipationForProgrammingExercise()" below needs the Authentication object set.
        // In tests, this is done using e.g. @WithMockUser(username="student1", roles="USER"), but this does not work on this @BeforeEach method.
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(student1Login, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        projectKey1 = programmingExercise.getProjectKey();
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setAllowOfflineIde(true);
        programmingExercise.setTestRepositoryUrl(localVCSBaseUrl + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow(); // programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();
        // Set the correct repository URLs for the template and the solution participation.
        templateRepositorySlug = projectKey1.toLowerCase() + "-exercise";
        templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUrl(localVCSBaseUrl + "/git/" + projectKey1 + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        solutionRepositorySlug = projectKey1.toLowerCase() + "-solution";
        solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUrl(localVCSBaseUrl + "/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        assignmentRepositorySlug = (projectKey1 + "-" + student1Login).toLowerCase();

        // Add a participation for student1.
        studentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, student1Login);
        studentParticipation.setRepositoryUrl(String.format(localVCSBaseUrl + "/git/%s/%s.git", projectKey1, assignmentRepositorySlug));
        studentParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        // Add a participation for tutor1.
        teachingAssistantParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, tutor1Login);
        teachingAssistantParticipation.setRepositoryUrl(String.format(localVCSBaseUrl + "/git/%s/%s.git", projectKey1, (projectKey1 + "-" + tutor1Login).toLowerCase()));
        teachingAssistantParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(teachingAssistantParticipation);

        // Add a participation for instructor1.
        instructorParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, instructor1Login);
        instructorParticipation.setRepositoryUrl(String.format(localVCSBaseUrl + "/git/%s/%s.git", projectKey1, (projectKey1 + "-" + instructor1Login).toLowerCase()));
        instructorParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(instructorParticipation);

        localVCLocalCITestService.addTestCases(programmingExercise);

        mockDockerClientMethods();
    }
}
