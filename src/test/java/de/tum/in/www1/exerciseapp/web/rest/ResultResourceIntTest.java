package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
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
 * Test class for the ResultResource REST controller.
 *
 * @see ResultResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class ResultResourceIntTest {

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

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restResultMockMvc;

    private Result result;

    private final ResultResource resultResource;

    public ResultResourceIntTest(ResultResource resultResource) {
        this.resultResource = resultResource;
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.restResultMockMvc = MockMvcBuilders.standaloneSetup(resultResource)
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
    public static Result createEntity(EntityManager em) {
        Result result = new Result()
            .resultString(DEFAULT_RESULT_STRING)
            .completionDate(DEFAULT_COMPLETION_DATE)
            .successful(DEFAULT_SUCCESSFUL)
            .buildArtifact(DEFAULT_BUILD_ARTIFACT)
            .score(DEFAULT_SCORE);
        return result;
    }

    @Before
    public void initTest() {
        result = createEntity(em);
    }

    @Test
    @Transactional
    public void createResult() throws Exception {
        int databaseSizeBeforeCreate = resultRepository.findAll().size();

        // Create the Result
        restResultMockMvc.perform(post("/api/results")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(result)))
            .andExpect(status().isCreated());

        // Validate the Result in the database
        List<Result> resultList = resultRepository.findAll();
        assertThat(resultList).hasSize(databaseSizeBeforeCreate + 1);
        Result testResult = resultList.get(resultList.size() - 1);
        assertThat(testResult.getResultString()).isEqualTo(DEFAULT_RESULT_STRING);
        assertThat(testResult.getCompletionDate()).isEqualTo(DEFAULT_COMPLETION_DATE);
        assertThat(testResult.isSuccessful()).isEqualTo(DEFAULT_SUCCESSFUL);
        assertThat(testResult.isBuildArtifact()).isEqualTo(DEFAULT_BUILD_ARTIFACT);
        assertThat(testResult.getScore()).isEqualTo(DEFAULT_SCORE);
    }

    @Test
    @Transactional
    public void createResultWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = resultRepository.findAll().size();

        // Create the Result with an existing ID
        result.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restResultMockMvc.perform(post("/api/results")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(result)))
            .andExpect(status().isBadRequest());

        // Validate the Result in the database
        List<Result> resultList = resultRepository.findAll();
        assertThat(resultList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllResults() throws Exception {
        // Initialize the database
        resultRepository.saveAndFlush(result);

        // Get all the resultList
        restResultMockMvc.perform(get("/api/results?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(result.getId().intValue())))
            .andExpect(jsonPath("$.[*].resultString").value(hasItem(DEFAULT_RESULT_STRING.toString())))
            .andExpect(jsonPath("$.[*].completionDate").value(hasItem(sameInstant(DEFAULT_COMPLETION_DATE))))
            .andExpect(jsonPath("$.[*].successful").value(hasItem(DEFAULT_SUCCESSFUL.booleanValue())))
            .andExpect(jsonPath("$.[*].buildArtifact").value(hasItem(DEFAULT_BUILD_ARTIFACT.booleanValue())))
            .andExpect(jsonPath("$.[*].score").value(hasItem(DEFAULT_SCORE.intValue())));
    }

    @Test
    @Transactional
    public void getResult() throws Exception {
        // Initialize the database
        resultRepository.saveAndFlush(result);

        // Get the result
        restResultMockMvc.perform(get("/api/results/{id}", result.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(result.getId().intValue()))
            .andExpect(jsonPath("$.resultString").value(DEFAULT_RESULT_STRING.toString()))
            .andExpect(jsonPath("$.completionDate").value(sameInstant(DEFAULT_COMPLETION_DATE)))
            .andExpect(jsonPath("$.successful").value(DEFAULT_SUCCESSFUL.booleanValue()))
            .andExpect(jsonPath("$.buildArtifact").value(DEFAULT_BUILD_ARTIFACT.booleanValue()))
            .andExpect(jsonPath("$.score").value(DEFAULT_SCORE.intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingResult() throws Exception {
        // Get the result
        restResultMockMvc.perform(get("/api/results/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateResult() throws Exception {
        // Initialize the database
        resultRepository.saveAndFlush(result);
        int databaseSizeBeforeUpdate = resultRepository.findAll().size();

        // Update the result
        Result updatedResult = resultRepository.findOne(result.getId());
        updatedResult
            .resultString(UPDATED_RESULT_STRING)
            .completionDate(UPDATED_COMPLETION_DATE)
            .successful(UPDATED_SUCCESSFUL)
            .buildArtifact(UPDATED_BUILD_ARTIFACT)
            .score(UPDATED_SCORE);

        restResultMockMvc.perform(put("/api/results")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedResult)))
            .andExpect(status().isOk());

        // Validate the Result in the database
        List<Result> resultList = resultRepository.findAll();
        assertThat(resultList).hasSize(databaseSizeBeforeUpdate);
        Result testResult = resultList.get(resultList.size() - 1);
        assertThat(testResult.getResultString()).isEqualTo(UPDATED_RESULT_STRING);
        assertThat(testResult.getCompletionDate()).isEqualTo(UPDATED_COMPLETION_DATE);
        assertThat(testResult.isSuccessful()).isEqualTo(UPDATED_SUCCESSFUL);
        assertThat(testResult.isBuildArtifact()).isEqualTo(UPDATED_BUILD_ARTIFACT);
        assertThat(testResult.getScore()).isEqualTo(UPDATED_SCORE);
    }

    @Test
    @Transactional
    public void updateNonExistingResult() throws Exception {
        int databaseSizeBeforeUpdate = resultRepository.findAll().size();

        // Create the Result

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restResultMockMvc.perform(put("/api/results")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(result)))
            .andExpect(status().isCreated());

        // Validate the Result in the database
        List<Result> resultList = resultRepository.findAll();
        assertThat(resultList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteResult() throws Exception {
        // Initialize the database
        resultRepository.saveAndFlush(result);
        int databaseSizeBeforeDelete = resultRepository.findAll().size();

        // Get the result
        restResultMockMvc.perform(delete("/api/results/{id}", result.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<Result> resultList = resultRepository.findAll();
        assertThat(resultList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Result.class);
        Result result1 = new Result();
        result1.setId(1L);
        Result result2 = new Result();
        result2.setId(result1.getId());
        assertThat(result1).isEqualTo(result2);
        result2.setId(2L);
        assertThat(result1).isNotEqualTo(result2);
        result1.setId(null);
        assertThat(result1).isNotEqualTo(result2);
    }
}
