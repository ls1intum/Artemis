package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

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

import de.tum.in.www1.artemis.domain.*;
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

    private Set<ExerciseHint> hints;

    @BeforeEach
    public void setUp() {
        databse.addUsers(1, 1, 1);
        baseCourse = databse.addCourseWithOneProgrammingExerciseAndTestCases();
        additionalEmptyCourse = databse.addEmptyCourse();
        programmingExercise = databse.loadProgrammingExerciseWithEagerReferences();
        hints = databse.addHintsToExercise(programmingExercise);
        databse.addHintsToProblemStatement(programmingExercise);

        // Load again to fetch changes to statement and hints while keeping eager refs
        programmingExercise = databse.loadProgrammingExerciseWithEagerReferences();
    }

    @AfterEach
    public void tearDown() {
        databse.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExerciseBasis_baseReferencesGotCloned() throws MalformedURLException {
        final var toBeImported = createToBeImported();
        final var templateRepoName = toBeImported.getProjectKey().toLowerCase() + "-exercise";
        final var solutionRepoName = toBeImported.getProjectKey().toLowerCase() + "-solution";
        final var testRepoName = toBeImported.getProjectKey().toLowerCase() + "-tests";
        when(bitbucketService.getCloneURL(toBeImported.getProjectKey(), templateRepoName)).thenReturn(new URL("http://template-url"));
        when(bitbucketService.getCloneURL(toBeImported.getProjectKey(), solutionRepoName)).thenReturn(new URL("http://solution-url"));
        when(bitbucketService.getCloneURL(toBeImported.getProjectKey(), testRepoName)).thenReturn(new URL("http://tests-url"));

        final var newlyImported = programmingExerciseService.importProgrammingExerciseBasis(programmingExercise, toBeImported);

        assertThat(newlyImported.getId()).isNotEqualTo(programmingExercise.getId());
        assertThat(newlyImported != programmingExercise).isTrue();
        assertThat(newlyImported.getTemplateParticipation().getId()).isNotEqualTo(programmingExercise.getTemplateParticipation().getId());
        assertThat(newlyImported.getSolutionParticipation().getId()).isNotEqualTo(programmingExercise.getSolutionParticipation().getId());
        assertThat(newlyImported.getPackageFolderName()).isEqualTo(programmingExercise.getPackageFolderName());
        assertThat(newlyImported.getBuildAndTestStudentSubmissionsAfterDueDate()).isEqualTo(programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
    }

    private ProgrammingExercise createToBeImported() {
        return ModelFactory.generateToBeImportedProgrammingExercise("Test", "TST", programmingExercise, additionalEmptyCourse);
    }
}
