package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.MultipleChoiceSubmittedAnswer;
import de.tum.in.www1.exerciseapp.domain.SubmittedAnswer;
import de.tum.in.www1.exerciseapp.repository.SubmittedAnswerRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the SubmittedAnswerResource REST controller.
 *
 * @see SubmittedAnswerResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class SubmittedAnswerResourceIntTest {

    private static final Double DEFAULT_SCORE_IN_POINTS = 1D;
    private static final Double UPDATED_SCORE_IN_POINTS = 2D;

    @Autowired
    private SubmittedAnswerRepository submittedAnswerRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restSubmittedAnswerMockMvc;

    private SubmittedAnswer submittedAnswer;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final SubmittedAnswerResource submittedAnswerResource = new SubmittedAnswerResource(submittedAnswerRepository);
        this.restSubmittedAnswerMockMvc = MockMvcBuilders.standaloneSetup(submittedAnswerResource)
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
    public static SubmittedAnswer createEntity(EntityManager em) {
        SubmittedAnswer submittedAnswer = new MultipleChoiceSubmittedAnswer()
            .scoreInPoints(DEFAULT_SCORE_IN_POINTS);
        return submittedAnswer;
    }

    @Before
    public void initTest() {
        submittedAnswer = createEntity(em);
    }

    @Test
    @Transactional
    public void createSubmittedAnswer() throws Exception {
        int databaseSizeBeforeCreate = submittedAnswerRepository.findAll().size();

        // Create the SubmittedAnswer
        restSubmittedAnswerMockMvc.perform(post("/api/submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(submittedAnswer)))
            .andExpect(status().isCreated());

        // Validate the SubmittedAnswer in the database
        List<SubmittedAnswer> submittedAnswerList = submittedAnswerRepository.findAll();
        assertThat(submittedAnswerList).hasSize(databaseSizeBeforeCreate + 1);
        SubmittedAnswer testSubmittedAnswer = submittedAnswerList.get(submittedAnswerList.size() - 1);
        assertThat(testSubmittedAnswer.getScoreInPoints()).isEqualTo(DEFAULT_SCORE_IN_POINTS);
    }

    @Test
    @Transactional
    public void createSubmittedAnswerWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = submittedAnswerRepository.findAll().size();

        // Create the SubmittedAnswer with an existing ID
        submittedAnswer.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restSubmittedAnswerMockMvc.perform(post("/api/submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(submittedAnswer)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<SubmittedAnswer> submittedAnswerList = submittedAnswerRepository.findAll();
        assertThat(submittedAnswerList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllSubmittedAnswers() throws Exception {
        // Initialize the database
        submittedAnswerRepository.saveAndFlush(submittedAnswer);

        // Get all the submittedAnswerList
        restSubmittedAnswerMockMvc.perform(get("/api/submitted-answers?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(submittedAnswer.getId().intValue())))
            .andExpect(jsonPath("$.[*].scoreInPoints").value(hasItem(DEFAULT_SCORE_IN_POINTS.doubleValue())));
    }

    @Test
    @Transactional
    public void getSubmittedAnswer() throws Exception {
        // Initialize the database
        submittedAnswerRepository.saveAndFlush(submittedAnswer);

        // Get the submittedAnswer
        restSubmittedAnswerMockMvc.perform(get("/api/submitted-answers/{id}", submittedAnswer.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(submittedAnswer.getId().intValue()))
            .andExpect(jsonPath("$.scoreInPoints").value(DEFAULT_SCORE_IN_POINTS.doubleValue()));
    }

    @Test
    @Transactional
    public void getNonExistingSubmittedAnswer() throws Exception {
        // Get the submittedAnswer
        restSubmittedAnswerMockMvc.perform(get("/api/submitted-answers/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateSubmittedAnswer() throws Exception {
        // Initialize the database
        submittedAnswerRepository.saveAndFlush(submittedAnswer);
        int databaseSizeBeforeUpdate = submittedAnswerRepository.findAll().size();

        // Update the submittedAnswer
        SubmittedAnswer updatedSubmittedAnswer = submittedAnswerRepository.findOne(submittedAnswer.getId());
        updatedSubmittedAnswer
            .scoreInPoints(UPDATED_SCORE_IN_POINTS);

        restSubmittedAnswerMockMvc.perform(put("/api/submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedSubmittedAnswer)))
            .andExpect(status().isOk());

        // Validate the SubmittedAnswer in the database
        List<SubmittedAnswer> submittedAnswerList = submittedAnswerRepository.findAll();
        assertThat(submittedAnswerList).hasSize(databaseSizeBeforeUpdate);
        SubmittedAnswer testSubmittedAnswer = submittedAnswerList.get(submittedAnswerList.size() - 1);
        assertThat(testSubmittedAnswer.getScoreInPoints()).isEqualTo(UPDATED_SCORE_IN_POINTS);
    }

    @Test
    @Transactional
    public void updateNonExistingSubmittedAnswer() throws Exception {
        int databaseSizeBeforeUpdate = submittedAnswerRepository.findAll().size();

        // Create the SubmittedAnswer

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restSubmittedAnswerMockMvc.perform(put("/api/submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(submittedAnswer)))
            .andExpect(status().isCreated());

        // Validate the SubmittedAnswer in the database
        List<SubmittedAnswer> submittedAnswerList = submittedAnswerRepository.findAll();
        assertThat(submittedAnswerList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteSubmittedAnswer() throws Exception {
        // Initialize the database
        submittedAnswerRepository.saveAndFlush(submittedAnswer);
        int databaseSizeBeforeDelete = submittedAnswerRepository.findAll().size();

        // Get the submittedAnswer
        restSubmittedAnswerMockMvc.perform(delete("/api/submitted-answers/{id}", submittedAnswer.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<SubmittedAnswer> submittedAnswerList = submittedAnswerRepository.findAll();
        assertThat(submittedAnswerList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(SubmittedAnswer.class);
        SubmittedAnswer submittedAnswer1 = new MultipleChoiceSubmittedAnswer();
        submittedAnswer1.setId(1L);
        SubmittedAnswer submittedAnswer2 = new MultipleChoiceSubmittedAnswer();
        submittedAnswer2.setId(submittedAnswer1.getId());
        assertThat(submittedAnswer1).isEqualTo(submittedAnswer2);
        submittedAnswer2.setId(2L);
        assertThat(submittedAnswer1).isNotEqualTo(submittedAnswer2);
        submittedAnswer1.setId(null);
        assertThat(submittedAnswer1).isNotEqualTo(submittedAnswer2);
    }
}
