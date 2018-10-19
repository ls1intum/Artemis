package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.ExerciseResult;
import de.tum.in.www1.artemis.repository.ExerciseResultRepository;
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


import static de.tum.in.www1.artemis.web.rest.TestUtil.sameInstant;
import static de.tum.in.www1.artemis.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
/**
 * Test class for the ExerciseResultResource REST controller.
 *
 * @see ExerciseResultResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class ExerciseResultResourceIntTest {

    private static final String DEFAULT_RESULT_STRING = "AAAAAAAAAA";
    private static final String UPDATED_RESULT_STRING = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_COMPLETION_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_COMPLETION_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final Boolean DEFAULT_SUCCESSFUL = false;
    private static final Boolean UPDATED_SUCCESSFUL = true;

    private static final Boolean DEFAULT_BUILD_ARTIFACT = false;
    private static final Boolean UPDATED_BUILD_ARTIFACT = true;

    private static final Long DEFAULT_SCORE = 1L;
    private static final Long UPDATED_SCORE = 2L;

    private static final Boolean DEFAULT_RATED = false;
    private static final Boolean UPDATED_RATED = true;

    private static final Boolean DEFAULT_HAS_FEEDBACK = false;
    private static final Boolean UPDATED_HAS_FEEDBACK = true;

    private static final AssessmentType DEFAULT_ASSESSMENT_TYPE = AssessmentType.AUTOMATIC;
    private static final AssessmentType UPDATED_ASSESSMENT_TYPE = AssessmentType.MANUAL;

    @Autowired
    private ExerciseResultRepository exerciseResultRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restExerciseResultMockMvc;

    private ExerciseResult exerciseResult;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final ExerciseResultResource exerciseResultResource = new ExerciseResultResource(exerciseResultRepository);
        this.restExerciseResultMockMvc = MockMvcBuilders.standaloneSetup(exerciseResultResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setConversionService(createFormattingConversionService())
            .setMessageConverters(jacksonMessageConverter).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ExerciseResult createEntity(EntityManager em) {
        ExerciseResult exerciseResult = new ExerciseResult()
            .resultString(DEFAULT_RESULT_STRING)
            .completionDate(DEFAULT_COMPLETION_DATE)
            .successful(DEFAULT_SUCCESSFUL)
            .buildArtifact(DEFAULT_BUILD_ARTIFACT)
            .score(DEFAULT_SCORE)
            .rated(DEFAULT_RATED)
            .hasFeedback(DEFAULT_HAS_FEEDBACK)
            .assessmentType(DEFAULT_ASSESSMENT_TYPE);
        return exerciseResult;
    }

    @Before
    public void initTest() {
        exerciseResult = createEntity(em);
    }

    @Test
    @Transactional
    public void createExerciseResult() throws Exception {
        int databaseSizeBeforeCreate = exerciseResultRepository.findAll().size();

        // Create the ExerciseResult
        restExerciseResultMockMvc.perform(post("/api/exercise-results")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(exerciseResult)))
            .andExpect(status().isCreated());

        // Validate the ExerciseResult in the database
        List<ExerciseResult> exerciseResultList = exerciseResultRepository.findAll();
        assertThat(exerciseResultList).hasSize(databaseSizeBeforeCreate + 1);
        ExerciseResult testExerciseResult = exerciseResultList.get(exerciseResultList.size() - 1);
        assertThat(testExerciseResult.getResultString()).isEqualTo(DEFAULT_RESULT_STRING);
        assertThat(testExerciseResult.getCompletionDate()).isEqualTo(DEFAULT_COMPLETION_DATE);
        assertThat(testExerciseResult.isSuccessful()).isEqualTo(DEFAULT_SUCCESSFUL);
        assertThat(testExerciseResult.isBuildArtifact()).isEqualTo(DEFAULT_BUILD_ARTIFACT);
        assertThat(testExerciseResult.getScore()).isEqualTo(DEFAULT_SCORE);
        assertThat(testExerciseResult.isRated()).isEqualTo(DEFAULT_RATED);
        assertThat(testExerciseResult.isHasFeedback()).isEqualTo(DEFAULT_HAS_FEEDBACK);
        assertThat(testExerciseResult.getAssessmentType()).isEqualTo(DEFAULT_ASSESSMENT_TYPE);
    }

    @Test
    @Transactional
    public void createExerciseResultWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = exerciseResultRepository.findAll().size();

        // Create the ExerciseResult with an existing ID
        exerciseResult.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restExerciseResultMockMvc.perform(post("/api/exercise-results")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(exerciseResult)))
            .andExpect(status().isBadRequest());

        // Validate the ExerciseResult in the database
        List<ExerciseResult> exerciseResultList = exerciseResultRepository.findAll();
        assertThat(exerciseResultList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllExerciseResults() throws Exception {
        // Initialize the database
        exerciseResultRepository.saveAndFlush(exerciseResult);

        // Get all the exerciseResultList
        restExerciseResultMockMvc.perform(get("/api/exercise-results?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(exerciseResult.getId().intValue())))
            .andExpect(jsonPath("$.[*].resultString").value(hasItem(DEFAULT_RESULT_STRING.toString())))
            .andExpect(jsonPath("$.[*].completionDate").value(hasItem(sameInstant(DEFAULT_COMPLETION_DATE))))
            .andExpect(jsonPath("$.[*].successful").value(hasItem(DEFAULT_SUCCESSFUL.booleanValue())))
            .andExpect(jsonPath("$.[*].buildArtifact").value(hasItem(DEFAULT_BUILD_ARTIFACT.booleanValue())))
            .andExpect(jsonPath("$.[*].score").value(hasItem(DEFAULT_SCORE.intValue())))
            .andExpect(jsonPath("$.[*].rated").value(hasItem(DEFAULT_RATED.booleanValue())))
            .andExpect(jsonPath("$.[*].hasFeedback").value(hasItem(DEFAULT_HAS_FEEDBACK.booleanValue())))
            .andExpect(jsonPath("$.[*].assessmentType").value(hasItem(DEFAULT_ASSESSMENT_TYPE.toString())));
    }
    
    @Test
    @Transactional
    public void getExerciseResult() throws Exception {
        // Initialize the database
        exerciseResultRepository.saveAndFlush(exerciseResult);

        // Get the exerciseResult
        restExerciseResultMockMvc.perform(get("/api/exercise-results/{id}", exerciseResult.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(exerciseResult.getId().intValue()))
            .andExpect(jsonPath("$.resultString").value(DEFAULT_RESULT_STRING.toString()))
            .andExpect(jsonPath("$.completionDate").value(sameInstant(DEFAULT_COMPLETION_DATE)))
            .andExpect(jsonPath("$.successful").value(DEFAULT_SUCCESSFUL.booleanValue()))
            .andExpect(jsonPath("$.buildArtifact").value(DEFAULT_BUILD_ARTIFACT.booleanValue()))
            .andExpect(jsonPath("$.score").value(DEFAULT_SCORE.intValue()))
            .andExpect(jsonPath("$.rated").value(DEFAULT_RATED.booleanValue()))
            .andExpect(jsonPath("$.hasFeedback").value(DEFAULT_HAS_FEEDBACK.booleanValue()))
            .andExpect(jsonPath("$.assessmentType").value(DEFAULT_ASSESSMENT_TYPE.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingExerciseResult() throws Exception {
        // Get the exerciseResult
        restExerciseResultMockMvc.perform(get("/api/exercise-results/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateExerciseResult() throws Exception {
        // Initialize the database
        exerciseResultRepository.saveAndFlush(exerciseResult);

        int databaseSizeBeforeUpdate = exerciseResultRepository.findAll().size();

        // Update the exerciseResult
        ExerciseResult updatedExerciseResult = exerciseResultRepository.findById(exerciseResult.getId()).get();
        // Disconnect from session so that the updates on updatedExerciseResult are not directly saved in db
        em.detach(updatedExerciseResult);
        updatedExerciseResult
            .resultString(UPDATED_RESULT_STRING)
            .completionDate(UPDATED_COMPLETION_DATE)
            .successful(UPDATED_SUCCESSFUL)
            .buildArtifact(UPDATED_BUILD_ARTIFACT)
            .score(UPDATED_SCORE)
            .rated(UPDATED_RATED)
            .hasFeedback(UPDATED_HAS_FEEDBACK)
            .assessmentType(UPDATED_ASSESSMENT_TYPE);

        restExerciseResultMockMvc.perform(put("/api/exercise-results")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedExerciseResult)))
            .andExpect(status().isOk());

        // Validate the ExerciseResult in the database
        List<ExerciseResult> exerciseResultList = exerciseResultRepository.findAll();
        assertThat(exerciseResultList).hasSize(databaseSizeBeforeUpdate);
        ExerciseResult testExerciseResult = exerciseResultList.get(exerciseResultList.size() - 1);
        assertThat(testExerciseResult.getResultString()).isEqualTo(UPDATED_RESULT_STRING);
        assertThat(testExerciseResult.getCompletionDate()).isEqualTo(UPDATED_COMPLETION_DATE);
        assertThat(testExerciseResult.isSuccessful()).isEqualTo(UPDATED_SUCCESSFUL);
        assertThat(testExerciseResult.isBuildArtifact()).isEqualTo(UPDATED_BUILD_ARTIFACT);
        assertThat(testExerciseResult.getScore()).isEqualTo(UPDATED_SCORE);
        assertThat(testExerciseResult.isRated()).isEqualTo(UPDATED_RATED);
        assertThat(testExerciseResult.isHasFeedback()).isEqualTo(UPDATED_HAS_FEEDBACK);
        assertThat(testExerciseResult.getAssessmentType()).isEqualTo(UPDATED_ASSESSMENT_TYPE);
    }

    @Test
    @Transactional
    public void updateNonExistingExerciseResult() throws Exception {
        int databaseSizeBeforeUpdate = exerciseResultRepository.findAll().size();

        // Create the ExerciseResult

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restExerciseResultMockMvc.perform(put("/api/exercise-results")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(exerciseResult)))
            .andExpect(status().isBadRequest());

        // Validate the ExerciseResult in the database
        List<ExerciseResult> exerciseResultList = exerciseResultRepository.findAll();
        assertThat(exerciseResultList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteExerciseResult() throws Exception {
        // Initialize the database
        exerciseResultRepository.saveAndFlush(exerciseResult);

        int databaseSizeBeforeDelete = exerciseResultRepository.findAll().size();

        // Get the exerciseResult
        restExerciseResultMockMvc.perform(delete("/api/exercise-results/{id}", exerciseResult.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<ExerciseResult> exerciseResultList = exerciseResultRepository.findAll();
        assertThat(exerciseResultList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ExerciseResult.class);
        ExerciseResult exerciseResult1 = new ExerciseResult();
        exerciseResult1.setId(1L);
        ExerciseResult exerciseResult2 = new ExerciseResult();
        exerciseResult2.setId(exerciseResult1.getId());
        assertThat(exerciseResult1).isEqualTo(exerciseResult2);
        exerciseResult2.setId(2L);
        assertThat(exerciseResult1).isNotEqualTo(exerciseResult2);
        exerciseResult1.setId(null);
        assertThat(exerciseResult1).isNotEqualTo(exerciseResult2);
    }
}
