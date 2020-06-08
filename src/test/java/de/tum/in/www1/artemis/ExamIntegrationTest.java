package de.tum.in.www1.artemis;

import java.util.HashSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.connector.jira.JiraRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ParticipationRepository participationRepo;

    @Autowired
    SubmissionRepository submissionRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    CustomAuditEventRepository auditEventRepo;

    @Autowired
    JiraRequestMockProvider jiraRequestMockProvider;

    @Autowired
    UserRepository userRepo;

    @Autowired
    UserService userService;

    private final int numberOfStudents = 4;

    private final int numberOfTutors = 5;

    private final int numberOfInstructors = 1;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(numberOfStudents, numberOfTutors, numberOfInstructors);

        // Add users that are not in the course
        userRepo.save(ModelFactory.generateActivatedUser("tutor6"));
        userRepo.save(ModelFactory.generateActivatedUser("instructor2"));
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testSaveExamToDatabase() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());

        // TODO: create one EXAM and store in the database (using repositories)
        // TODO: create some EXERCISE GROUPs with exercises and store in the database (using repositories)
        // TODO: create some STUDENT EXAMs with related entities

    }

}
