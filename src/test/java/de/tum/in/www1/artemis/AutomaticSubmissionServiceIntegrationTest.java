package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.scheduled.AutomaticSubmissionService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class AutomaticSubmissionServiceIntegrationTest {

    @Autowired
    AutomaticSubmissionService automaticSubmissionService;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    TextSubmissionRepository textSubmissionRepo;

    @Autowired
    DatabaseUtilService database;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private ModelingSubmission unsubmittedModelingSubmission;

    private ModelingSubmission submittedModelingSubmission;

    private TextSubmission unsubmittedTextSubmission;

    private TextSubmission submittedTextSubmission;

    @Before
    public void initTestCase() throws Exception {
        database.resetDatabase();
        database.addUsers(2, 0, 0);
        database.addCourseWithOneModelingAndOneTextExercise();
        modelingExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        textExercise = (TextExercise) exerciseRepo.findAll().get(1);
        String validModel = database.loadFileFromResources("test-data/model-submission/model.54727.json");

        unsubmittedModelingSubmission = ModelFactory.generateModelingSubmission(validModel, false);
        submittedModelingSubmission = ModelFactory.generateModelingSubmission(validModel, true);
        unsubmittedTextSubmission = ModelFactory.generateTextSubmission("This is an example submission", Language.ENGLISH, false);
        submittedTextSubmission = ModelFactory.generateTextSubmission("This is an example submission", Language.ENGLISH, true);

        unsubmittedModelingSubmission.setSubmissionDate(ZonedDateTime.now().minusHours(2));
        submittedModelingSubmission.setSubmissionDate(ZonedDateTime.now().minusHours(2));
        unsubmittedTextSubmission.setSubmissionDate(ZonedDateTime.now().minusHours(2));
        submittedTextSubmission.setSubmissionDate(ZonedDateTime.now().minusHours(2));

        SecurityUtils.setAuthorizationObject();

        unsubmittedModelingSubmission = database.addModelingSubmission(modelingExercise, unsubmittedModelingSubmission, "student1");
        submittedModelingSubmission = database.addModelingSubmission(modelingExercise, submittedModelingSubmission, "student2");
        unsubmittedTextSubmission = database.addTextSubmission(textExercise, unsubmittedTextSubmission, "student1");
        submittedTextSubmission = database.addTextSubmission(textExercise, submittedTextSubmission, "student2");

        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(null);
    }

    @Test
    public void testAutomaticSubmission() throws Exception {
        database.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusHours(1));
        database.updateExerciseDueDate(textExercise.getId(), ZonedDateTime.now().minusHours(1));

        automaticSubmissionService.run();

        Optional<ModelingSubmission> storedModelingSubmission = modelingSubmissionRepo.findById(unsubmittedModelingSubmission.getId());
        assertThat(storedModelingSubmission).isPresent();
        assertThat(storedModelingSubmission.get().isSubmitted()).isTrue();
        assertThat(storedModelingSubmission.get().getType()).isEqualTo(SubmissionType.TIMEOUT);
        assertThat(storedModelingSubmission.get().getParticipation().getInitializationState()).isEqualTo(InitializationState.FINISHED);

        storedModelingSubmission = modelingSubmissionRepo.findById(submittedModelingSubmission.getId());
        assertThat(storedModelingSubmission).isPresent();
        assertThat(storedModelingSubmission.get().isSubmitted()).isTrue();
        assertThat(storedModelingSubmission.get().getType()).isNotEqualTo(SubmissionType.TIMEOUT);

        Optional<TextSubmission> storedTextSubmission = textSubmissionRepo.findById(unsubmittedTextSubmission.getId());
        assertThat(storedTextSubmission).isPresent();
        assertThat(storedTextSubmission.get().isSubmitted()).isTrue();
        assertThat(storedTextSubmission.get().getType()).isEqualTo(SubmissionType.TIMEOUT);
        assertThat(storedTextSubmission.get().getParticipation().getInitializationState()).isEqualTo(InitializationState.FINISHED);

        storedTextSubmission = textSubmissionRepo.findById(submittedTextSubmission.getId());
        assertThat(storedTextSubmission).isPresent();
        assertThat(storedTextSubmission.get().isSubmitted()).isTrue();
        assertThat(storedTextSubmission.get().getType()).isNotEqualTo(SubmissionType.TIMEOUT);
    }

    @Test
    public void testAutomaticSubmission_dueDateNotOver() throws Exception {
        automaticSubmissionService.run();

        Optional<ModelingSubmission> storedModelingSubmission = modelingSubmissionRepo.findById(unsubmittedModelingSubmission.getId());
        assertThat(storedModelingSubmission).isPresent();
        assertThat(storedModelingSubmission.get().isSubmitted()).isFalse();
        assertThat(storedModelingSubmission.get().getType()).isNotEqualTo(SubmissionType.TIMEOUT);

        Optional<TextSubmission> storedTextSubmission = textSubmissionRepo.findById(unsubmittedTextSubmission.getId());
        assertThat(storedTextSubmission).isPresent();
        assertThat(storedTextSubmission.get().isSubmitted()).isFalse();
        assertThat(storedTextSubmission.get().getType()).isNotEqualTo(SubmissionType.TIMEOUT);
    }

    @Test
    public void testAutomaticSubmission_submissionDateAfterDueDate() throws Exception {
        unsubmittedModelingSubmission.setSubmissionDate(ZonedDateTime.now());
        modelingSubmissionRepo.save(unsubmittedModelingSubmission);
        unsubmittedTextSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmissionRepo.save(unsubmittedTextSubmission);
        database.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusHours(1));
        database.updateExerciseDueDate(textExercise.getId(), ZonedDateTime.now().minusHours(1));

        automaticSubmissionService.run();

        Optional<ModelingSubmission> storedModelingSubmission = modelingSubmissionRepo.findById(unsubmittedModelingSubmission.getId());
        assertThat(storedModelingSubmission).isPresent();
        assertThat(storedModelingSubmission.get().isSubmitted()).isFalse();
        assertThat(storedModelingSubmission.get().getType()).isNotEqualTo(SubmissionType.TIMEOUT);

        Optional<TextSubmission> storedTextSubmission = textSubmissionRepo.findById(unsubmittedTextSubmission.getId());
        assertThat(storedTextSubmission).isPresent();
        assertThat(storedTextSubmission.get().isSubmitted()).isFalse();
        assertThat(storedTextSubmission.get().getType()).isNotEqualTo(SubmissionType.TIMEOUT);
    }
}
