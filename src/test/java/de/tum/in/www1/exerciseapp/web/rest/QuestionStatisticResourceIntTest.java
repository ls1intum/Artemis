package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;
import de.tum.in.www1.exerciseapp.domain.MultipleChoiceQuestionStatistic;
import de.tum.in.www1.exerciseapp.domain.QuestionStatistic;
import de.tum.in.www1.exerciseapp.repository.QuestionStatisticRepository;
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
 * Test class for the QuestionStatisticResource REST controller.
 *
 * @see QuestionStatisticResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class QuestionStatisticResourceIntTest {

    private static final Integer DEFAULT_RATED_CORRECT_COUNTER = 1;
    private static final Integer UPDATED_RATED_CORRECT_COUNTER = 2;

    private static final Integer DEFAULT_UN_RATED_CORRECT_COUNTER = 1;
    private static final Integer UPDATED_UN_RATED_CORRECT_COUNTER = 2;

    @Autowired
    private QuestionStatisticRepository questionStatisticRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restQuestionStatisticMockMvc;

    private QuestionStatistic questionStatistic;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        QuestionStatisticResource questionStatisticResource = new QuestionStatisticResource(questionStatisticRepository);
        this.restQuestionStatisticMockMvc = MockMvcBuilders.standaloneSetup(questionStatisticResource)
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
    public static QuestionStatistic createEntity(EntityManager em) {
        QuestionStatistic questionStatistic = new MultipleChoiceQuestionStatistic()
            .ratedCorrectCounter(DEFAULT_RATED_CORRECT_COUNTER)
            .unRatedCorrectCounter(DEFAULT_UN_RATED_CORRECT_COUNTER);
        return questionStatistic;
    }

    @Before
    public void initTest() {
        questionStatistic = createEntity(em);
    }

    @Test
    @Transactional
    public void createQuestionStatistic() throws Exception {
        int databaseSizeBeforeCreate = questionStatisticRepository.findAll().size();

        // Create the QuestionStatistic
        restQuestionStatisticMockMvc.perform(post("/api/question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(questionStatistic)))
            .andExpect(status().isCreated());

        // Validate the QuestionStatistic in the database
        List<QuestionStatistic> questionStatisticList = questionStatisticRepository.findAll();
        assertThat(questionStatisticList).hasSize(databaseSizeBeforeCreate + 1);
        QuestionStatistic testQuestionStatistic = questionStatisticList.get(questionStatisticList.size() - 1);
        assertThat(testQuestionStatistic.getRatedCorrectCounter()).isEqualTo(DEFAULT_RATED_CORRECT_COUNTER);
        assertThat(testQuestionStatistic.getUnRatedCorrectCounter()).isEqualTo(DEFAULT_UN_RATED_CORRECT_COUNTER);
    }

    @Test
    @Transactional
    public void createQuestionStatisticWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = questionStatisticRepository.findAll().size();

        // Create the QuestionStatistic with an existing ID
        questionStatistic.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restQuestionStatisticMockMvc.perform(post("/api/question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(questionStatistic)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<QuestionStatistic> questionStatisticList = questionStatisticRepository.findAll();
        assertThat(questionStatisticList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllQuestionStatistics() throws Exception {
        // Initialize the database
        questionStatisticRepository.saveAndFlush(questionStatistic);

        // Get all the questionStatisticList
        restQuestionStatisticMockMvc.perform(get("/api/question-statistics?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(questionStatistic.getId().intValue())))
            .andExpect(jsonPath("$.[*].ratedCorrectCounter").value(hasItem(DEFAULT_RATED_CORRECT_COUNTER)))
            .andExpect(jsonPath("$.[*].unRatedCorrectCounter").value(hasItem(DEFAULT_UN_RATED_CORRECT_COUNTER)));
    }

    @Test
    @Transactional
    public void getQuestionStatistic() throws Exception {
        // Initialize the database
        questionStatisticRepository.saveAndFlush(questionStatistic);

        // Get the questionStatistic
        restQuestionStatisticMockMvc.perform(get("/api/question-statistics/{id}", questionStatistic.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(questionStatistic.getId().intValue()))
            .andExpect(jsonPath("$.ratedCorrectCounter").value(DEFAULT_RATED_CORRECT_COUNTER))
            .andExpect(jsonPath("$.unRatedCorrectCounter").value(DEFAULT_UN_RATED_CORRECT_COUNTER));
    }

    @Test
    @Transactional
    public void getNonExistingQuestionStatistic() throws Exception {
        // Get the questionStatistic
        restQuestionStatisticMockMvc.perform(get("/api/question-statistics/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateQuestionStatistic() throws Exception {
        // Initialize the database
        questionStatisticRepository.saveAndFlush(questionStatistic);
        int databaseSizeBeforeUpdate = questionStatisticRepository.findAll().size();

        // Update the questionStatistic
        QuestionStatistic updatedQuestionStatistic = questionStatisticRepository.findOne(questionStatistic.getId());
        updatedQuestionStatistic
            .ratedCorrectCounter(UPDATED_RATED_CORRECT_COUNTER)
            .unRatedCorrectCounter(UPDATED_UN_RATED_CORRECT_COUNTER);

        restQuestionStatisticMockMvc.perform(put("/api/question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedQuestionStatistic)))
            .andExpect(status().isOk());

        // Validate the QuestionStatistic in the database
        List<QuestionStatistic> questionStatisticList = questionStatisticRepository.findAll();
        assertThat(questionStatisticList).hasSize(databaseSizeBeforeUpdate);
        QuestionStatistic testQuestionStatistic = questionStatisticList.get(questionStatisticList.size() - 1);
        assertThat(testQuestionStatistic.getRatedCorrectCounter()).isEqualTo(UPDATED_RATED_CORRECT_COUNTER);
        assertThat(testQuestionStatistic.getUnRatedCorrectCounter()).isEqualTo(UPDATED_UN_RATED_CORRECT_COUNTER);
    }

    @Test
    @Transactional
    public void updateNonExistingQuestionStatistic() throws Exception {
        int databaseSizeBeforeUpdate = questionStatisticRepository.findAll().size();

        // Create the QuestionStatistic

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restQuestionStatisticMockMvc.perform(put("/api/question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(questionStatistic)))
            .andExpect(status().isCreated());

        // Validate the QuestionStatistic in the database
        List<QuestionStatistic> questionStatisticList = questionStatisticRepository.findAll();
        assertThat(questionStatisticList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteQuestionStatistic() throws Exception {
        // Initialize the database
        questionStatisticRepository.saveAndFlush(questionStatistic);
        int databaseSizeBeforeDelete = questionStatisticRepository.findAll().size();

        // Get the questionStatistic
        restQuestionStatisticMockMvc.perform(delete("/api/question-statistics/{id}", questionStatistic.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<QuestionStatistic> questionStatisticList = questionStatisticRepository.findAll();
        assertThat(questionStatisticList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(QuestionStatistic.class);
        QuestionStatistic questionStatistic1 = new MultipleChoiceQuestionStatistic();
        questionStatistic1.setId(1L);
        QuestionStatistic questionStatistic2 = new MultipleChoiceQuestionStatistic();
        questionStatistic2.setId(questionStatistic1.getId());
        assertThat(questionStatistic1).isEqualTo(questionStatistic2);
        questionStatistic2.setId(2L);
        assertThat(questionStatistic1).isNotEqualTo(questionStatistic2);
        questionStatistic1.setId(null);
        assertThat(questionStatistic1).isNotEqualTo(questionStatistic2);
    }
}
