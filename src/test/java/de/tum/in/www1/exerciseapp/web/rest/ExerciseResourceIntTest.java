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
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;

import static de.tum.in.www1.exerciseapp.web.rest.TestUtil.sameInstant;
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

    private static final String DEFAULT_TITLE = "AAAAAAAAAA";
    private static final String UPDATED_TITLE = "BBBBBBBBBB";

    private static final String DEFAULT_BASE_PROJECT_KEY = "AAAAAAAAAA";
    private static final String UPDATED_BASE_PROJECT_KEY = "BBBBBBBBBB";

    private static final String DEFAULT_BASE_REPOSITORY_SLUG = "AAAAAAAAAA";
    private static final String UPDATED_BASE_REPOSITORY_SLUG = "BBBBBBBBBB";

    private static final String DEFAULT_BASE_BUILD_PLAN_SLUG = "AAAAAAAAAA";
    private static final String UPDATED_BASE_BUILD_PLAN_SLUG = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_RELEASE_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_RELEASE_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final ZonedDateTime DEFAULT_DUE_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_DUE_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

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

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final ExerciseResource exerciseResource = new ExerciseResource(exerciseRepository);
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
        exercise.setBaseProjectKey(DEFAULT_BASE_PROJECT_KEY);
        exercise.setBaseRepositorySlug(DEFAULT_BASE_REPOSITORY_SLUG);
        exercise.setBaseBuildPlanSlug(DEFAULT_BASE_BUILD_PLAN_SLUG);
        exercise.setReleaseDate(DEFAULT_RELEASE_DATE);
        exercise.setDueDate(DEFAULT_DUE_DATE);
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
        List<Exercise> exerciseList = exerciseRepository.findAll();
        assertThat(exerciseList).hasSize(databaseSizeBeforeCreate + 1);
        Exercise testExercise = exerciseList.get(exerciseList.size() - 1);
        assertThat(testExercise.getTitle()).isEqualTo(DEFAULT_TITLE);
        assertThat(testExercise.getBaseProjectKey()).isEqualTo(DEFAULT_BASE_PROJECT_KEY);
        assertThat(testExercise.getBaseRepositorySlug()).isEqualTo(DEFAULT_BASE_REPOSITORY_SLUG);
        assertThat(testExercise.getBaseBuildPlanSlug()).isEqualTo(DEFAULT_BASE_BUILD_PLAN_SLUG);
        assertThat(testExercise.getReleaseDate()).isEqualTo(DEFAULT_RELEASE_DATE);
        assertThat(testExercise.getDueDate()).isEqualTo(DEFAULT_DUE_DATE);
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

        // Get all the exerciseList
        restExerciseMockMvc.perform(get("/api/exercises?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(exercise.getId().intValue())))
            .andExpect(jsonPath("$.[*].title").value(hasItem(DEFAULT_TITLE.toString())))
            .andExpect(jsonPath("$.[*].baseProjectKey").value(hasItem(DEFAULT_BASE_PROJECT_KEY.toString())))
            .andExpect(jsonPath("$.[*].baseRepositorySlug").value(hasItem(DEFAULT_BASE_REPOSITORY_SLUG.toString())))
            .andExpect(jsonPath("$.[*].baseBuildPlanSlug").value(hasItem(DEFAULT_BASE_BUILD_PLAN_SLUG.toString())))
            .andExpect(jsonPath("$.[*].releaseDate").value(hasItem(sameInstant(DEFAULT_RELEASE_DATE))))
            .andExpect(jsonPath("$.[*].dueDate").value(hasItem(sameInstant(DEFAULT_DUE_DATE))));
    }

    @Test
    @Transactional
    public void getExercise() throws Exception {
        // Initialize the database
        exerciseRepository.saveAndFlush(exercise);

        // Get the exercise
        restExerciseMockMvc.perform(get("/api/exercises/{id}", exercise.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(exercise.getId().intValue()))
            .andExpect(jsonPath("$.title").value(DEFAULT_TITLE.toString()))
            .andExpect(jsonPath("$.baseProjectKey").value(DEFAULT_BASE_PROJECT_KEY.toString()))
            .andExpect(jsonPath("$.baseRepositorySlug").value(DEFAULT_BASE_REPOSITORY_SLUG.toString()))
            .andExpect(jsonPath("$.baseBuildPlanSlug").value(DEFAULT_BASE_BUILD_PLAN_SLUG.toString()))
            .andExpect(jsonPath("$.releaseDate").value(sameInstant(DEFAULT_RELEASE_DATE)))
            .andExpect(jsonPath("$.dueDate").value(sameInstant(DEFAULT_DUE_DATE)));
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
        Exercise updatedExercise = exerciseRepository.findOne(exercise.getId());
        updatedExercise.setTitle(UPDATED_TITLE);
        updatedExercise.setBaseProjectKey(UPDATED_BASE_PROJECT_KEY);
        updatedExercise.setBaseRepositorySlug(UPDATED_BASE_REPOSITORY_SLUG);
        updatedExercise.setBaseBuildPlanSlug(UPDATED_BASE_BUILD_PLAN_SLUG);
        updatedExercise.setReleaseDate(UPDATED_RELEASE_DATE);
        updatedExercise.setDueDate(UPDATED_DUE_DATE);

        restExerciseMockMvc.perform(put("/api/exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedExercise)))
            .andExpect(status().isOk());

        // Validate the Exercise in the database
        List<Exercise> exerciseList = exerciseRepository.findAll();
        assertThat(exerciseList).hasSize(databaseSizeBeforeUpdate);
        Exercise testExercise = exerciseList.get(exerciseList.size() - 1);
        assertThat(testExercise.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testExercise.getBaseProjectKey()).isEqualTo(UPDATED_BASE_PROJECT_KEY);
        assertThat(testExercise.getBaseRepositorySlug()).isEqualTo(UPDATED_BASE_REPOSITORY_SLUG);
        assertThat(testExercise.getBaseBuildPlanSlug()).isEqualTo(UPDATED_BASE_BUILD_PLAN_SLUG);
        assertThat(testExercise.getReleaseDate()).isEqualTo(UPDATED_RELEASE_DATE);
        assertThat(testExercise.getDueDate()).isEqualTo(UPDATED_DUE_DATE);
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
