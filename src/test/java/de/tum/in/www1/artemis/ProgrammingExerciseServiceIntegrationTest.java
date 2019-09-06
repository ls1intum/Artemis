package de.tum.in.www1.artemis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockBeans;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.connectors.BambooService;
import de.tum.in.www1.artemis.service.connectors.BitbucketService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles({ "artemis", "bamboo", "bitbucket", "jira" })
@MockBeans({ @MockBean(BambooService.class), @MockBean(BitbucketService.class) })
public class ProgrammingExerciseServiceIntegrationTest {

    @Autowired
    BambooService bambooService;

    @Autowired
    BitbucketService bitbucketService;

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    DatabaseUtilService databse;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @BeforeEach
    public void setUp() {
        databse.addUsers(1, 1, 1);
        databse.addCourseWithOneProgrammingExerciseAndTestCases();
    }

    @AfterEach
    public void tearDown() {
        databse.resetDatabase();
    }
}
