package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;
import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.repository.ExerciseRepository;
import de.tum.in.www1.exerciseapp.web.rest.errors.ExceptionTranslator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the ExerciseResource REST controller.
 *
 * @see ExerciseResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ExerciseApplicationApp.class)
public class ExerciseResourceIntTest {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("Z"));

    private static final String DEFAULT_TITLE = "AAAAA";
    private static final String UPDATED_TITLE = "BBBBB";
    private static final String DEFAULT_BASE_REPOSITORY_URL = "AAAAA";
    private static final String UPDATED_BASE_REPOSITORY_URL = "BBBBB";
    private static final String DEFAULT_BASE_BUILD_PLAN_ID = "AAAAA";
    private static final String UPDATED_BASE_BUILD_PLAN_ID = "BBBBB";

    private static final Boolean DEFAULT_PUBLISH_BUILD_PLAN_URL = false;
    private static final Boolean UPDATED_PUBLISH_BUILD_PLAN_URL = true;

    private static final ZonedDateTime DEFAULT_RELEASE_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneId.systemDefault());
    private static final ZonedDateTime UPDATED_RELEASE_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);
    private static final String DEFAULT_RELEASE_DATE_STR = dateTimeFormatter.format(DEFAULT_RELEASE_DATE);

    private static final ZonedDateTime DEFAULT_DUE_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneId.systemDefault());
    private static final ZonedDateTime UPDATED_DUE_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);
    private static final String DEFAULT_DUE_DATE_STR = dateTimeFormatter.format(DEFAULT_DUE_DATE);

    private static final Boolean DEFAULT_ALLOW_ONLINE_EDITOR = false;
    private static final Boolean UPDATED_ALLOW_ONLINE_EDITOR = true;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restExerciseMockMvc;

    private Exercise exercise;

    private final ExerciseResource exerciseResource;

    public ExerciseResourceIntTest(ExerciseResource exerciseResource) {
        this.exerciseResource = exerciseResource;
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.restExerciseMockMvc = MockMvcBuilders.standaloneSetup(exerciseResource)
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
    public static Exercise createEntity(EntityManager em) {
        Exercise exercise = new Exercise();
        exercise.setTitle(DEFAULT_TITLE);
        exercise.setBaseRepositoryUrl(DEFAULT_BASE_REPOSITORY_URL);
        exercise.setBaseBuildPlanId(DEFAULT_BASE_BUILD_PLAN_ID);
        exercise.setPublishBuildPlanUrl(DEFAULT_PUBLISH_BUILD_PLAN_URL);
        exercise.setReleaseDate(DEFAULT_RELEASE_DATE);
        exercise.setDueDate(DEFAULT_DUE_DATE);
        exercise.setAllowOnlineEditor(DEFAULT_ALLOW_ONLINE_EDITOR);
        return exercise;
    }

    @Before
    public void initTest() {
        exercise = createEntity(em);
    }

    @Test
    @Transactional
    public void createExercise() throws Exception {
        int databaseSizeBeforeCreate = exerciseRepository.findAll().size();

        // Create the Exercise
        restExerciseMockMvc.perform(post("/api/exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(exercise)))
            .andExpect(status().isCreated());

        // Validate the Exercise in the database
        List<Exercise> exercises = exerciseRepository.findAll();
        assertThat(exercises).hasSize(databaseSizeBeforeCreate + 1);
        Exercise testExercise = exercises.get(exercises.size() - 1);
        assertThat(testExercise.getTitle()).isEqualTo(DEFAULT_TITLE);
        assertThat(testExercise.getBaseRepositoryUrl()).isEqualTo(DEFAULT_BASE_REPOSITORY_URL);
        assertThat(testExercise.getBaseBuildPlanId()).isEqualTo(DEFAULT_BASE_BUILD_PLAN_ID);
        assertThat(testExercise.isPublishBuildPlanUrl()).isEqualTo(DEFAULT_PUBLISH_BUILD_PLAN_URL);
        assertThat(testExercise.getReleaseDate()).isEqualTo(DEFAULT_RELEASE_DATE);
        assertThat(testExercise.getDueDate()).isEqualTo(DEFAULT_DUE_DATE);
        assertThat(testExercise.isAllowOnlineEditor()).isEqualTo(DEFAULT_ALLOW_ONLINE_EDITOR);
    }

    @Test
    @Transactional
    public void createExerciseWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = exerciseRepository.findAll().size();

        // Create the Exercise with an existing ID
        exercise.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restExerciseMockMvc.perform(post("/api/exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(exercise)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<Exercise> exerciseList = exerciseRepository.findAll();
        assertThat(exerciseList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllExercises() throws Exception {
        // Initialize the database
        exerciseRepository.saveAndFlush(exercise);

        // Get all the exercises
        restExerciseMockMvc.perform(get("/api/exercises?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.[*].id").value(hasItem(exercise.getId().intValue())))
            .andExpect(jsonPath("$.[*].title").value(hasItem(DEFAULT_TITLE.toString())))
            .andExpect(jsonPath("$.[*].baseRepositoryUrl").value(hasItem(DEFAULT_BASE_REPOSITORY_URL.toString())))
            .andExpect(jsonPath("$.[*].baseBuildPlanId").value(hasItem(DEFAULT_BASE_BUILD_PLAN_ID.toString())))
            .andExpect(jsonPath("$.[*].publishBuildPlanUrl").value(hasItem(DEFAULT_PUBLISH_BUILD_PLAN_URL.booleanValue())))
            .andExpect(jsonPath("$.[*].releaseDate").value(hasItem(DEFAULT_RELEASE_DATE_STR)))
            .andExpect(jsonPath("$.[*].dueDate").value(hasItem(DEFAULT_DUE_DATE_STR)))
            .andExpect(jsonPath("$.[*].allowOnlineEditor").value(hasItem(DEFAULT_ALLOW_ONLINE_EDITOR.booleanValue())));
    }

    @Test
    @Transactional
    public void getExercise() throws Exception {
        // Initialize the database
        exerciseRepository.saveAndFlush(exercise);

        // Get the exercise
        restExerciseMockMvc.perform(get("/api/exercises/{id}", exercise.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(exercise.getId().intValue()))
            .andExpect(jsonPath("$.title").value(DEFAULT_TITLE.toString()))
            .andExpect(jsonPath("$.baseRepositoryUrl").value(DEFAULT_BASE_REPOSITORY_URL.toString()))
            .andExpect(jsonPath("$.baseBuildPlanId").value(DEFAULT_BASE_BUILD_PLAN_ID.toString()))
            .andExpect(jsonPath("$.publishBuildPlanUrl").value(DEFAULT_PUBLISH_BUILD_PLAN_URL.booleanValue()))
            .andExpect(jsonPath("$.releaseDate").value(DEFAULT_RELEASE_DATE_STR))
            .andExpect(jsonPath("$.dueDate").value(DEFAULT_DUE_DATE_STR))
            .andExpect(jsonPath("$.allowOnlineEditor").value(DEFAULT_ALLOW_ONLINE_EDITOR.booleanValue()));
    }

    @Test
    @Transactional
    public void getNonExistingExercise() throws Exception {
        // Get the exercise
        restExerciseMockMvc.perform(get("/api/exercises/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateExercise() throws Exception {
        // Initialize the database
        exerciseRepository.saveAndFlush(exercise);
        int databaseSizeBeforeUpdate = exerciseRepository.findAll().size();

        // Update the exercise
        Exercise updatedExercise = new Exercise();
        updatedExercise.setId(exercise.getId());
        updatedExercise.setTitle(UPDATED_TITLE);
        updatedExercise.setBaseRepositoryUrl(UPDATED_BASE_REPOSITORY_URL);
        updatedExercise.setBaseBuildPlanId(UPDATED_BASE_BUILD_PLAN_ID);
        updatedExercise.setPublishBuildPlanUrl(UPDATED_PUBLISH_BUILD_PLAN_URL);
        updatedExercise.setReleaseDate(UPDATED_RELEASE_DATE);
        updatedExercise.setDueDate(UPDATED_DUE_DATE);
        updatedExercise.setAllowOnlineEditor(UPDATED_ALLOW_ONLINE_EDITOR);

        restExerciseMockMvc.perform(put("/api/exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedExercise)))
            .andExpect(status().isOk());

        // Validate the Exercise in the database
        List<Exercise> exercises = exerciseRepository.findAll();
        assertThat(exercises).hasSize(databaseSizeBeforeUpdate);
        Exercise testExercise = exercises.get(exercises.size() - 1);
        assertThat(testExercise.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testExercise.getBaseRepositoryUrl()).isEqualTo(UPDATED_BASE_REPOSITORY_URL);
        assertThat(testExercise.getBaseBuildPlanId()).isEqualTo(UPDATED_BASE_BUILD_PLAN_ID);
        assertThat(testExercise.isPublishBuildPlanUrl()).isEqualTo(UPDATED_PUBLISH_BUILD_PLAN_URL);
        assertThat(testExercise.getReleaseDate()).isEqualTo(UPDATED_RELEASE_DATE);
        assertThat(testExercise.getDueDate()).isEqualTo(UPDATED_DUE_DATE);
        assertThat(testExercise.isAllowOnlineEditor()).isEqualTo(UPDATED_ALLOW_ONLINE_EDITOR);
    }

    @Test
    @Transactional
    public void updateNonExistingExercise() throws Exception {
        int databaseSizeBeforeUpdate = exerciseRepository.findAll().size();

        // Create the Exercise

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restExerciseMockMvc.perform(put("/api/exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(exercise)))
            .andExpect(status().isCreated());

        // Validate the Exercise in the database
        List<Exercise> exerciseList = exerciseRepository.findAll();
        assertThat(exerciseList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteExercise() throws Exception {
        // Initialize the database
        exerciseRepository.saveAndFlush(exercise);
        int databaseSizeBeforeDelete = exerciseRepository.findAll().size();

        // Get the exercise
        restExerciseMockMvc.perform(delete("/api/exercises/{id}", exercise.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<Exercise> exerciseList = exerciseRepository.findAll();
        assertThat(exerciseList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Exercise.class);
        Exercise exercise1 = new Exercise();
        exercise1.setId(1L);
        Exercise exercise2 = new Exercise();
        exercise2.setId(exercise1.getId());
        assertThat(exercise1).isEqualTo(exercise2);
        exercise2.setId(2L);
        assertThat(exercise1).isNotEqualTo(exercise2);
        exercise1.setId(null);
        assertThat(exercise1).isNotEqualTo(exercise2);
    }
}
