package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.QuizSubmission;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
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
import java.util.List;


import static de.tum.in.www1.artemis.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the QuizSubmissionResource REST controller.
 *
 * @see QuizSubmissionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class QuizSubmissionResourceIntTest {

    private static final Double DEFAULT_SCORE_IN_POINTS = 1D;
    private static final Double UPDATED_SCORE_IN_POINTS = 2D;

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restQuizSubmissionMockMvc;

    private QuizSubmission quizSubmission;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final QuizSubmissionResource quizSubmissionResource = new QuizSubmissionResource(quizSubmissionRepository);
        this.restQuizSubmissionMockMvc = MockMvcBuilders.standaloneSetup(quizSubmissionResource)
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
    public static QuizSubmission createEntity(EntityManager em) {
        QuizSubmission quizSubmission = new QuizSubmission()
            .scoreInPoints(DEFAULT_SCORE_IN_POINTS);
        return quizSubmission;
    }

    @Before
    public void initTest() {
        quizSubmission = createEntity(em);
    }

    @Test
    @Transactional
    public void createQuizSubmission() throws Exception {
        int databaseSizeBeforeCreate = quizSubmissionRepository.findAll().size();

        // Create the QuizSubmission
        restQuizSubmissionMockMvc.perform(post("/api/quiz-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizSubmission)))
            .andExpect(status().isCreated());

        // Validate the QuizSubmission in the database
        List<QuizSubmission> quizSubmissionList = quizSubmissionRepository.findAll();
        assertThat(quizSubmissionList).hasSize(databaseSizeBeforeCreate + 1);
        QuizSubmission testQuizSubmission = quizSubmissionList.get(quizSubmissionList.size() - 1);
        assertThat(testQuizSubmission.getScoreInPoints()).isEqualTo(DEFAULT_SCORE_IN_POINTS);
    }

    @Test
    @Transactional
    public void createQuizSubmissionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = quizSubmissionRepository.findAll().size();

        // Create the QuizSubmission with an existing ID
        quizSubmission.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restQuizSubmissionMockMvc.perform(post("/api/quiz-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizSubmission)))
            .andExpect(status().isBadRequest());

        // Validate the QuizSubmission in the database
        List<QuizSubmission> quizSubmissionList = quizSubmissionRepository.findAll();
        assertThat(quizSubmissionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllQuizSubmissions() throws Exception {
        // Initialize the database
        quizSubmissionRepository.saveAndFlush(quizSubmission);

        // Get all the quizSubmissionList
        restQuizSubmissionMockMvc.perform(get("/api/quiz-submissions?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(quizSubmission.getId().intValue())))
            .andExpect(jsonPath("$.[*].scoreInPoints").value(hasItem(DEFAULT_SCORE_IN_POINTS.doubleValue())));
    }
    
    @Test
    @Transactional
    public void getQuizSubmission() throws Exception {
        // Initialize the database
        quizSubmissionRepository.saveAndFlush(quizSubmission);

        // Get the quizSubmission
        restQuizSubmissionMockMvc.perform(get("/api/quiz-submissions/{id}", quizSubmission.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(quizSubmission.getId().intValue()))
            .andExpect(jsonPath("$.scoreInPoints").value(DEFAULT_SCORE_IN_POINTS.doubleValue()));
    }

    @Test
    @Transactional
    public void getNonExistingQuizSubmission() throws Exception {
        // Get the quizSubmission
        restQuizSubmissionMockMvc.perform(get("/api/quiz-submissions/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateQuizSubmission() throws Exception {
        // Initialize the database
        quizSubmissionRepository.saveAndFlush(quizSubmission);

        int databaseSizeBeforeUpdate = quizSubmissionRepository.findAll().size();

        // Update the quizSubmission
        QuizSubmission updatedQuizSubmission = quizSubmissionRepository.findById(quizSubmission.getId()).get();
        // Disconnect from session so that the updates on updatedQuizSubmission are not directly saved in db
        em.detach(updatedQuizSubmission);
        updatedQuizSubmission
            .scoreInPoints(UPDATED_SCORE_IN_POINTS);

        restQuizSubmissionMockMvc.perform(put("/api/quiz-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedQuizSubmission)))
            .andExpect(status().isOk());

        // Validate the QuizSubmission in the database
        List<QuizSubmission> quizSubmissionList = quizSubmissionRepository.findAll();
        assertThat(quizSubmissionList).hasSize(databaseSizeBeforeUpdate);
        QuizSubmission testQuizSubmission = quizSubmissionList.get(quizSubmissionList.size() - 1);
        assertThat(testQuizSubmission.getScoreInPoints()).isEqualTo(UPDATED_SCORE_IN_POINTS);
    }

    @Test
    @Transactional
    public void updateNonExistingQuizSubmission() throws Exception {
        int databaseSizeBeforeUpdate = quizSubmissionRepository.findAll().size();

        // Create the QuizSubmission

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restQuizSubmissionMockMvc.perform(put("/api/quiz-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizSubmission)))
            .andExpect(status().isBadRequest());

        // Validate the QuizSubmission in the database
        List<QuizSubmission> quizSubmissionList = quizSubmissionRepository.findAll();
        assertThat(quizSubmissionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteQuizSubmission() throws Exception {
        // Initialize the database
        quizSubmissionRepository.saveAndFlush(quizSubmission);

        int databaseSizeBeforeDelete = quizSubmissionRepository.findAll().size();

        // Get the quizSubmission
        restQuizSubmissionMockMvc.perform(delete("/api/quiz-submissions/{id}", quizSubmission.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<QuizSubmission> quizSubmissionList = quizSubmissionRepository.findAll();
        assertThat(quizSubmissionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(QuizSubmission.class);
        QuizSubmission quizSubmission1 = new QuizSubmission();
        quizSubmission1.setId(1L);
        QuizSubmission quizSubmission2 = new QuizSubmission();
        quizSubmission2.setId(quizSubmission1.getId());
        assertThat(quizSubmission1).isEqualTo(quizSubmission2);
        quizSubmission2.setId(2L);
        assertThat(quizSubmission1).isNotEqualTo(quizSubmission2);
        quizSubmission1.setId(null);
        assertThat(quizSubmission1).isNotEqualTo(quizSubmission2);
    }
}
