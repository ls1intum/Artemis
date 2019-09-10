package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockBeans;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.connectors.BambooService;
import de.tum.in.www1.artemis.service.connectors.BitbucketService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

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

    private Course baseCourse;

    private Course additionalEmptyCourse;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    public void setUp() {
        databse.addUsers(1, 1, 1);
        baseCourse = databse.addCourseWithOneProgrammingExerciseAndTestCases();
        additionalEmptyCourse = databse.addEmptyCourse();
        programmingExercise = databse.loadProgrammingExercisesWithEagerReferences().get(0);
    }

    @AfterEach
    public void tearDown() {
        databse.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExerciseBasis_baseReferencesGotCloned() {
        final ProgrammingExercise toBeImported = createToBeImported();

        final ProgrammingExercise newlyImported = programmingExerciseService.importProgrammingExerciseBasis(toBeImported, programmingExercise.getId());

        assertThat(newlyImported.getId()).isNotEqualTo(programmingExercise.getId());
        assertThat(newlyImported != programmingExercise).isTrue();
        assertThat(newlyImported.getTemplateParticipation() != programmingExercise.getTemplateParticipation()).isTrue();
        assertThat(newlyImported.getTemplateParticipation().getId()).isNotEqualTo(programmingExercise.getTemplateParticipation().getId());
    }

    private ProgrammingExercise createToBeImported() {
        return ModelFactory.generateToBeImportedProgrammingExercise("Test", "TST", programmingExercise, additionalEmptyCourse);
    }
}
