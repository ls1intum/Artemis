package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;
import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.repository.ExerciseRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.hamcrest.Matchers.hasItem;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Test class for the ExerciseResource REST controller.
 *
 * @see ExerciseResource
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ExerciseApplicationApp.class)
@WebAppConfiguration
@IntegrationTest
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

    @Inject
    private ExerciseRepository exerciseRepository;

    @Inject
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Inject
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    private MockMvc restExerciseMockMvc;

    private Exercise exercise;

    @PostConstruct
    public void setup() {
        MockitoAnnotations.initMocks(this);
        ExerciseResource exerciseResource = new ExerciseResource();
        ReflectionTestUtils.setField(exerciseResource, "exerciseRepository", exerciseRepository);
        this.restExerciseMockMvc = MockMvcBuilders.standaloneSetup(exerciseResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setMessageConverters(jacksonMessageConverter).build();
    }

    @Before
    public void initTest() {
        exercise = new Exercise();
        exercise.setTitle(DEFAULT_TITLE);
        exercise.setBaseRepositoryUrl(DEFAULT_BASE_REPOSITORY_URL);
        exercise.setBaseBuildPlanId(DEFAULT_BASE_BUILD_PLAN_ID);
        exercise.setPublishBuildPlanUrl(DEFAULT_PUBLISH_BUILD_PLAN_URL);
        exercise.setReleaseDate(DEFAULT_RELEASE_DATE);
        exercise.setDueDate(DEFAULT_DUE_DATE);
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
                .andExpect(jsonPath("$.[*].dueDate").value(hasItem(DEFAULT_DUE_DATE_STR)));
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
            .andExpect(jsonPath("$.dueDate").value(DEFAULT_DUE_DATE_STR));
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
        List<Exercise> exercises = exerciseRepository.findAll();
        assertThat(exercises).hasSize(databaseSizeBeforeDelete - 1);
    }
}
