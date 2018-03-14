package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTEMiSApp;
import de.tum.in.www1.artemis.domain.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.ExceptionTranslator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the QuizExerciseResource REST controller.
 *
 * @see QuizExerciseResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class QuizExerciseResourceIntTest {

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_EXPLANATION = "AAAAAAAAAA";
    private static final String UPDATED_EXPLANATION = "BBBBBBBBBB";

    private static final Boolean DEFAULT_RANDOMIZE_QUESTION_ORDER = false;
    private static final Boolean UPDATED_RANDOMIZE_QUESTION_ORDER = true;

    private static final Integer DEFAULT_ALLOWED_NUMBER_OF_ATTEMPTS = 1;
    private static final Integer UPDATED_ALLOWED_NUMBER_OF_ATTEMPTS = 2;

    private static final Boolean DEFAULT_IS_VISIBLE_BEFORE_START = false;
    private static final Boolean UPDATED_IS_VISIBLE_BEFORE_START = true;

    private static final Boolean DEFAULT_IS_OPEN_FOR_PRACTICE = false;
    private static final Boolean UPDATED_IS_OPEN_FOR_PRACTICE = true;

    private static final Boolean DEFAULT_IS_PLANNED_TO_START = false;
    private static final Boolean UPDATED_IS_PLANNED_TO_START = true;

    private static final Integer DEFAULT_DURATION = 1;
    private static final Integer UPDATED_DURATION = 2;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private DragAndDropMappingRepository dragAndDropMappingRepository;

    @Autowired
    private AuthorizationCheckService authorizationCheckService;

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private StatisticService statisticService;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private QuizExerciseResource quizExerciseResource;

    @Autowired
    private EntityManager em;

    private MockMvc restQuizExerciseMockMvc;

    private QuizExercise quizExercise;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.restQuizExerciseMockMvc = MockMvcBuilders.standaloneSetup(quizExerciseResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setMessageConverters(jacksonMessageConverter).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static QuizExercise createEntity(EntityManager em) {
        QuizExercise quizExercise = new QuizExercise()
            .description(DEFAULT_DESCRIPTION)
            .explanation(DEFAULT_EXPLANATION)
            .randomizeQuestionOrder(DEFAULT_RANDOMIZE_QUESTION_ORDER)
            .allowedNumberOfAttempts(DEFAULT_ALLOWED_NUMBER_OF_ATTEMPTS)
            .isVisibleBeforeStart(DEFAULT_IS_VISIBLE_BEFORE_START)
            .isOpenForPractice(DEFAULT_IS_OPEN_FOR_PRACTICE)
            .isPlannedToStart(DEFAULT_IS_PLANNED_TO_START)
            .duration(DEFAULT_DURATION);
        return quizExercise;
    }

    @Before
    public void initTest() {
        quizExercise = createEntity(em);
    }

    @Test
    @Transactional
    public void createQuizExercise() throws Exception {
        int databaseSizeBeforeCreate = quizExerciseRepository.findAll().size();

        // Create the QuizExercise
        restQuizExerciseMockMvc.perform(post("/api/quiz-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizExercise)))
            .andExpect(status().isCreated());

        // Validate the QuizExercise in the database
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeCreate + 1);
        QuizExercise testQuizExercise = quizExerciseList.get(quizExerciseList.size() - 1);
        assertThat(testQuizExercise.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testQuizExercise.getExplanation()).isEqualTo(DEFAULT_EXPLANATION);
        assertThat(testQuizExercise.isRandomizeQuestionOrder()).isEqualTo(DEFAULT_RANDOMIZE_QUESTION_ORDER);
        assertThat(testQuizExercise.getAllowedNumberOfAttempts()).isEqualTo(DEFAULT_ALLOWED_NUMBER_OF_ATTEMPTS);
        assertThat(testQuizExercise.isIsVisibleBeforeStart()).isEqualTo(DEFAULT_IS_VISIBLE_BEFORE_START);
        assertThat(testQuizExercise.isIsOpenForPractice()).isEqualTo(DEFAULT_IS_OPEN_FOR_PRACTICE);
        assertThat(testQuizExercise.isIsPlannedToStart()).isEqualTo(DEFAULT_IS_PLANNED_TO_START);
        assertThat(testQuizExercise.getDuration()).isEqualTo(DEFAULT_DURATION);
    }

    @Test
    @Transactional
    public void createQuizExerciseWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = quizExerciseRepository.findAll().size();

        // Create the QuizExercise with an existing ID
        quizExercise.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restQuizExerciseMockMvc.perform(post("/api/quiz-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizExercise)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllQuizExercises() throws Exception {
        // Initialize the database
        quizExerciseRepository.saveAndFlush(quizExercise);

        // Get all the quizExerciseList
        restQuizExerciseMockMvc.perform(get("/api/quiz-exercises?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(quizExercise.getId().intValue())))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION.toString())))
            .andExpect(jsonPath("$.[*].explanation").value(hasItem(DEFAULT_EXPLANATION.toString())))
            .andExpect(jsonPath("$.[*].randomizeQuestionOrder").value(hasItem(DEFAULT_RANDOMIZE_QUESTION_ORDER.booleanValue())))
            .andExpect(jsonPath("$.[*].allowedNumberOfAttempts").value(hasItem(DEFAULT_ALLOWED_NUMBER_OF_ATTEMPTS)))
            .andExpect(jsonPath("$.[*].isVisibleBeforeStart").value(hasItem(DEFAULT_IS_VISIBLE_BEFORE_START.booleanValue())))
            .andExpect(jsonPath("$.[*].isOpenForPractice").value(hasItem(DEFAULT_IS_OPEN_FOR_PRACTICE.booleanValue())))
            .andExpect(jsonPath("$.[*].isPlannedToStart").value(hasItem(DEFAULT_IS_PLANNED_TO_START.booleanValue())))
            .andExpect(jsonPath("$.[*].duration").value(hasItem(DEFAULT_DURATION)));
    }

    @Test
    @Transactional
    public void getQuizExercise() throws Exception {
        // Initialize the database
        quizExerciseRepository.saveAndFlush(quizExercise);

        // Get the quizExercise
        restQuizExerciseMockMvc.perform(get("/api/quiz-exercises/{id}", quizExercise.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(quizExercise.getId().intValue()))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION.toString()))
            .andExpect(jsonPath("$.explanation").value(DEFAULT_EXPLANATION.toString()))
            .andExpect(jsonPath("$.randomizeQuestionOrder").value(DEFAULT_RANDOMIZE_QUESTION_ORDER.booleanValue()))
            .andExpect(jsonPath("$.allowedNumberOfAttempts").value(DEFAULT_ALLOWED_NUMBER_OF_ATTEMPTS))
            .andExpect(jsonPath("$.isVisibleBeforeStart").value(DEFAULT_IS_VISIBLE_BEFORE_START.booleanValue()))
            .andExpect(jsonPath("$.isOpenForPractice").value(DEFAULT_IS_OPEN_FOR_PRACTICE.booleanValue()))
            .andExpect(jsonPath("$.isPlannedToStart").value(DEFAULT_IS_PLANNED_TO_START.booleanValue()))
            .andExpect(jsonPath("$.duration").value(DEFAULT_DURATION));
    }

    @Test
    @Transactional
    public void getNonExistingQuizExercise() throws Exception {
        // Get the quizExercise
        restQuizExerciseMockMvc.perform(get("/api/quiz-exercises/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateQuizExercise() throws Exception {
        // Initialize the database
        quizExerciseRepository.saveAndFlush(quizExercise);
        int databaseSizeBeforeUpdate = quizExerciseRepository.findAll().size();

        // Update the quizExercise
        QuizExercise updatedQuizExercise = quizExerciseRepository.findOne(quizExercise.getId());
        updatedQuizExercise
            .description(UPDATED_DESCRIPTION)
            .explanation(UPDATED_EXPLANATION)
            .randomizeQuestionOrder(UPDATED_RANDOMIZE_QUESTION_ORDER)
            .allowedNumberOfAttempts(UPDATED_ALLOWED_NUMBER_OF_ATTEMPTS)
            .isVisibleBeforeStart(UPDATED_IS_VISIBLE_BEFORE_START)
            .isOpenForPractice(UPDATED_IS_OPEN_FOR_PRACTICE)
            .isPlannedToStart(UPDATED_IS_PLANNED_TO_START)
            .duration(UPDATED_DURATION);

        restQuizExerciseMockMvc.perform(put("/api/quiz-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedQuizExercise)))
            .andExpect(status().isOk());

        // Validate the QuizExercise in the database
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeUpdate);
        QuizExercise testQuizExercise = quizExerciseList.get(quizExerciseList.size() - 1);
        assertThat(testQuizExercise.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testQuizExercise.getExplanation()).isEqualTo(UPDATED_EXPLANATION);
        assertThat(testQuizExercise.isRandomizeQuestionOrder()).isEqualTo(UPDATED_RANDOMIZE_QUESTION_ORDER);
        assertThat(testQuizExercise.getAllowedNumberOfAttempts()).isEqualTo(UPDATED_ALLOWED_NUMBER_OF_ATTEMPTS);
        assertThat(testQuizExercise.isIsVisibleBeforeStart()).isEqualTo(UPDATED_IS_VISIBLE_BEFORE_START);
        assertThat(testQuizExercise.isIsOpenForPractice()).isEqualTo(UPDATED_IS_OPEN_FOR_PRACTICE);
        assertThat(testQuizExercise.isIsPlannedToStart()).isEqualTo(UPDATED_IS_PLANNED_TO_START);
        assertThat(testQuizExercise.getDuration()).isEqualTo(UPDATED_DURATION);
    }

    @Test
    @Transactional
    public void updateNonExistingQuizExercise() throws Exception {
        int databaseSizeBeforeUpdate = quizExerciseRepository.findAll().size();

        // Create the QuizExercise

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restQuizExerciseMockMvc.perform(put("/api/quiz-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizExercise)))
            .andExpect(status().isCreated());

        // Validate the QuizExercise in the database
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteQuizExercise() throws Exception {
        // Initialize the database
        quizExerciseRepository.saveAndFlush(quizExercise);
        int databaseSizeBeforeDelete = quizExerciseRepository.findAll().size();

        // Get the quizExercise
        restQuizExerciseMockMvc.perform(delete("/api/quiz-exercises/{id}", quizExercise.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(QuizExercise.class);
        QuizExercise quizExercise1 = new QuizExercise();
        quizExercise1.setId(1L);
        QuizExercise quizExercise2 = new QuizExercise();
        quizExercise2.setId(quizExercise1.getId());
        assertThat(quizExercise1).isEqualTo(quizExercise2);
        quizExercise2.setId(2L);
        assertThat(quizExercise1).isNotEqualTo(quizExercise2);
        quizExercise1.setId(null);
        assertThat(quizExercise1).isNotEqualTo(quizExercise2);
    }
}
