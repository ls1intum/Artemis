package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;
import de.tum.in.www1.exerciseapp.domain.QuizPointStatistic;
import de.tum.in.www1.exerciseapp.repository.QuizPointStatisticRepository;
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
 * Test class for the QuizPointStatisticResource REST controller.
 *
 * @see QuizPointStatisticResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class QuizPointStatisticResourceIntTest {

    @Autowired
    private QuizPointStatisticRepository quizPointStatisticRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restQuizPointStatisticMockMvc;

    private QuizPointStatistic quizPointStatistic;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        QuizPointStatisticResource quizPointStatisticResource = new QuizPointStatisticResource(quizPointStatisticRepository);
        this.restQuizPointStatisticMockMvc = MockMvcBuilders.standaloneSetup(quizPointStatisticResource)
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
    public static QuizPointStatistic createEntity(EntityManager em) {
        QuizPointStatistic quizPointStatistic = new QuizPointStatistic();
        return quizPointStatistic;
    }

    @Before
    public void initTest() {
        quizPointStatistic = createEntity(em);
    }

    @Test
    @Transactional
    public void createQuizPointStatistic() throws Exception {
        int databaseSizeBeforeCreate = quizPointStatisticRepository.findAll().size();

        // Create the QuizPointStatistic
        restQuizPointStatisticMockMvc.perform(post("/api/quiz-point-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizPointStatistic)))
            .andExpect(status().isCreated());

        // Validate the QuizPointStatistic in the database
        List<QuizPointStatistic> quizPointStatisticList = quizPointStatisticRepository.findAll();
        assertThat(quizPointStatisticList).hasSize(databaseSizeBeforeCreate + 1);
        QuizPointStatistic testQuizPointStatistic = quizPointStatisticList.get(quizPointStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void createQuizPointStatisticWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = quizPointStatisticRepository.findAll().size();

        // Create the QuizPointStatistic with an existing ID
        quizPointStatistic.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restQuizPointStatisticMockMvc.perform(post("/api/quiz-point-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizPointStatistic)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<QuizPointStatistic> quizPointStatisticList = quizPointStatisticRepository.findAll();
        assertThat(quizPointStatisticList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllQuizPointStatistics() throws Exception {
        // Initialize the database
        quizPointStatisticRepository.saveAndFlush(quizPointStatistic);

        // Get all the quizPointStatisticList
        restQuizPointStatisticMockMvc.perform(get("/api/quiz-point-statistics?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(quizPointStatistic.getId().intValue())));
    }

    @Test
    @Transactional
    public void getQuizPointStatistic() throws Exception {
        // Initialize the database
        quizPointStatisticRepository.saveAndFlush(quizPointStatistic);

        // Get the quizPointStatistic
        restQuizPointStatisticMockMvc.perform(get("/api/quiz-point-statistics/{id}", quizPointStatistic.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(quizPointStatistic.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingQuizPointStatistic() throws Exception {
        // Get the quizPointStatistic
        restQuizPointStatisticMockMvc.perform(get("/api/quiz-point-statistics/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateQuizPointStatistic() throws Exception {
        // Initialize the database
        quizPointStatisticRepository.saveAndFlush(quizPointStatistic);
        int databaseSizeBeforeUpdate = quizPointStatisticRepository.findAll().size();

        // Update the quizPointStatistic
        QuizPointStatistic updatedQuizPointStatistic = quizPointStatisticRepository.findOne(quizPointStatistic.getId());

        restQuizPointStatisticMockMvc.perform(put("/api/quiz-point-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedQuizPointStatistic)))
            .andExpect(status().isOk());

        // Validate the QuizPointStatistic in the database
        List<QuizPointStatistic> quizPointStatisticList = quizPointStatisticRepository.findAll();
        assertThat(quizPointStatisticList).hasSize(databaseSizeBeforeUpdate);
        QuizPointStatistic testQuizPointStatistic = quizPointStatisticList.get(quizPointStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingQuizPointStatistic() throws Exception {
        int databaseSizeBeforeUpdate = quizPointStatisticRepository.findAll().size();

        // Create the QuizPointStatistic

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restQuizPointStatisticMockMvc.perform(put("/api/quiz-point-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizPointStatistic)))
            .andExpect(status().isCreated());

        // Validate the QuizPointStatistic in the database
        List<QuizPointStatistic> quizPointStatisticList = quizPointStatisticRepository.findAll();
        assertThat(quizPointStatisticList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteQuizPointStatistic() throws Exception {
        // Initialize the database
        quizPointStatisticRepository.saveAndFlush(quizPointStatistic);
        int databaseSizeBeforeDelete = quizPointStatisticRepository.findAll().size();

        // Get the quizPointStatistic
        restQuizPointStatisticMockMvc.perform(delete("/api/quiz-point-statistics/{id}", quizPointStatistic.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<QuizPointStatistic> quizPointStatisticList = quizPointStatisticRepository.findAll();
        assertThat(quizPointStatisticList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(QuizPointStatistic.class);
        QuizPointStatistic quizPointStatistic1 = new QuizPointStatistic();
        quizPointStatistic1.setId(1L);
        QuizPointStatistic quizPointStatistic2 = new QuizPointStatistic();
        quizPointStatistic2.setId(quizPointStatistic1.getId());
        assertThat(quizPointStatistic1).isEqualTo(quizPointStatistic2);
        quizPointStatistic2.setId(2L);
        assertThat(quizPointStatistic1).isNotEqualTo(quizPointStatistic2);
        quizPointStatistic1.setId(null);
        assertThat(quizPointStatistic1).isNotEqualTo(quizPointStatistic2);
    }
}
