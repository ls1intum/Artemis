package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.connectors.BambooService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis, bamboo")
public class ResultServiceIntegrationTest {

    @MockBean
    BambooService continuousIntegrationServiceMock;

    @Autowired
    ResultService resultService;

    @Autowired
    ProgrammingExerciseTestCaseService programmingExerciseTestCaseService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    private ProgrammingExercise programmingExercise;

    private SolutionProgrammingExerciseParticipation solutionParticipation;

    private TemplateProgrammingExerciseParticipation templateParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private StudentParticipation studentParticipation;

    @BeforeEach
    public void reset() {
        database.addUsers(2, 2, 2);
        database.addCourseWithOneProgrammingExercise();
        programmingExercise = programmingExerciseRepository.findAll().get(0);
        // This is done to avoid an unproxy issue in the processNewResult method of the ResultService.
        solutionParticipation = solutionProgrammingExerciseRepository.findByProgrammingExerciseId(programmingExercise.getId()).get();
        templateParticipation = programmingExercise.getTemplateParticipation();
        programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");

        database.addCourseWithOneModelingExercise();
        ModelingExercise modelingExercise = modelingExerciseRepository.findAll().get(0);
        studentParticipation = database.addParticipationForExercise(modelingExercise, "student2");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult() throws Exception {
        Result result = new Result();
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1").positive(true));
        feedbacks.add(new Feedback().text("test2").positive(true));
        feedbacks.add(new Feedback().text("test4").positive(true));
        result.successful(false).feedbacks(feedbacks).score(20L);

        Set<ProgrammingExerciseTestCase> expectedTestCases = new HashSet<>();
        expectedTestCases.add(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test1").active(true).weight(1).id(1L));
        expectedTestCases.add(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test2").active(true).weight(1).id(2L));
        expectedTestCases.add(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test4").active(true).weight(1).id(3L));

        Object requestDummy = new Object();

        when(continuousIntegrationServiceMock.onBuildCompletedNew(solutionParticipation, requestDummy)).thenReturn(result);
        resultService.processNewProgrammingExerciseResult(solutionParticipation, requestDummy);

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseService.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).isEqualTo(expectedTestCases);
        assertThat(result.getScore()).isEqualTo(100L);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAProgrammingExerciseStudentParticipation() throws Exception {
        Result result = database.addResultToParticipation(programmingExerciseStudentParticipation);
        result = database.addFeedbacksToResult(result);

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(value = "student2", roles = "USER")
    public void shouldReturnTheResultDetailsForAStudentParticipation() throws Exception {
        Result result = database.addResultToParticipation(studentParticipation);
        result = database.addFeedbacksToResult(result);

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAStudentParticipation_studentForbidden() throws Exception {
        Result result = database.addResultToParticipation(studentParticipation);
        result = database.addFeedbacksToResult(result);

        request.getList("/api/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAProgrammingExerciseStudentParticipation_studentForbidden() throws Exception {
        Result result = database.addResultToParticipation(solutionParticipation);
        result = database.addFeedbacksToResult(result);

        request.getList("/api/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnNotFoundForNonExistingResult() throws Exception {
        Result result = database.addResultToParticipation(solutionParticipation);
        result = database.addFeedbacksToResult(result);

        request.getList("/api/results/" + 66 + "/details", HttpStatus.NOT_FOUND, Feedback.class);
    }
}
