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
    private static final String DEFAULT_SLUG = "AAAAA";
    private static final String UPDATED_SLUG = "BBBBB";
    private static final String DEFAULT_BASE_PROJECT_KEY = "AAAAA";
    private static final String UPDATED_BASE_PROJECT_KEY = "BBBBB";
    private static final String DEFAULT_BASE_REPOSITORY_SLUG = "AAAAA";
    private static final String UPDATED_BASE_REPOSITORY_SLUG = "BBBBB";
    private static final String DEFAULT_BASE_BUILD_PLAN_SLUG = "AAAAA";
    private static final String UPDATED_BASE_BUILD_PLAN_SLUG = "BBBBB";

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
        exercise.setSlug(DEFAULT_SLUG);
        exercise.setBaseProjectKey(DEFAULT_BASE_PROJECT_KEY);
        exercise.setBaseRepositorySlug(DEFAULT_BASE_REPOSITORY_SLUG);
        exercise.setBaseBuildPlanSlug(DEFAULT_BASE_BUILD_PLAN_SLUG);
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
        assertThat(testExercise.getSlug()).isEqualTo(DEFAULT_SLUG);
        assertThat(testExercise.getBaseProjectKey()).isEqualTo(DEFAULT_BASE_PROJECT_KEY);
        assertThat(testExercise.getBaseRepositorySlug()).isEqualTo(DEFAULT_BASE_REPOSITORY_SLUG);
        assertThat(testExercise.getBaseBuildPlanSlug()).isEqualTo(DEFAULT_BASE_BUILD_PLAN_SLUG);
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
                .andExpect(jsonPath("$.[*].slug").value(hasItem(DEFAULT_SLUG.toString())))
                .andExpect(jsonPath("$.[*].baseProjectKey").value(hasItem(DEFAULT_BASE_PROJECT_KEY.toString())))
                .andExpect(jsonPath("$.[*].baseRepositorySlug").value(hasItem(DEFAULT_BASE_REPOSITORY_SLUG.toString())))
                .andExpect(jsonPath("$.[*].baseBuildPlanSlug").value(hasItem(DEFAULT_BASE_BUILD_PLAN_SLUG.toString())))
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
            .andExpect(jsonPath("$.slug").value(DEFAULT_SLUG.toString()))
            .andExpect(jsonPath("$.baseProjectKey").value(DEFAULT_BASE_PROJECT_KEY.toString()))
            .andExpect(jsonPath("$.baseRepositorySlug").value(DEFAULT_BASE_REPOSITORY_SLUG.toString()))
            .andExpect(jsonPath("$.baseBuildPlanSlug").value(DEFAULT_BASE_BUILD_PLAN_SLUG.toString()))
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
        updatedExercise.setSlug(UPDATED_SLUG);
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
        List<Exercise> exercises = exerciseRepository.findAll();
        assertThat(exercises).hasSize(databaseSizeBeforeUpdate);
        Exercise testExercise = exercises.get(exercises.size() - 1);
        assertThat(testExercise.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testExercise.getSlug()).isEqualTo(UPDATED_SLUG);
        assertThat(testExercise.getBaseProjectKey()).isEqualTo(UPDATED_BASE_PROJECT_KEY);
        assertThat(testExercise.getBaseRepositorySlug()).isEqualTo(UPDATED_BASE_REPOSITORY_SLUG);
        assertThat(testExercise.getBaseBuildPlanSlug()).isEqualTo(UPDATED_BASE_BUILD_PLAN_SLUG);
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

    @Test
    @Transactional
    public void searchExercise() throws Exception {
        // Initialize the database
        exerciseRepository.saveAndFlush(exercise);

        // Search the exercise
        restExerciseMockMvc.perform(get("/api/_search/exercises?query=id:" + exercise.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.[*].id").value(hasItem(exercise.getId().intValue())))
            .andExpect(jsonPath("$.[*].title").value(hasItem(DEFAULT_TITLE.toString())))
            .andExpect(jsonPath("$.[*].slug").value(hasItem(DEFAULT_SLUG.toString())))
            .andExpect(jsonPath("$.[*].baseProjectKey").value(hasItem(DEFAULT_BASE_PROJECT_KEY.toString())))
            .andExpect(jsonPath("$.[*].baseRepositorySlug").value(hasItem(DEFAULT_BASE_REPOSITORY_SLUG.toString())))
            .andExpect(jsonPath("$.[*].baseBuildPlanSlug").value(hasItem(DEFAULT_BASE_BUILD_PLAN_SLUG.toString())))
            .andExpect(jsonPath("$.[*].releaseDate").value(hasItem(DEFAULT_RELEASE_DATE_STR)))
            .andExpect(jsonPath("$.[*].dueDate").value(hasItem(DEFAULT_DUE_DATE_STR)));
    }
}
