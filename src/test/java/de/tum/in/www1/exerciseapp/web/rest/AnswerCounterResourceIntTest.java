package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.AnswerCounter;
import de.tum.in.www1.exerciseapp.repository.AnswerCounterRepository;
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
 * Test class for the AnswerCounterResource REST controller.
 *
 * @see AnswerCounterResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class AnswerCounterResourceIntTest {

    private static final Integer DEFAULT_COUNTER = 1;
    private static final Integer UPDATED_COUNTER = 2;

    @Autowired
    private AnswerCounterRepository answerCounterRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restAnswerCounterMockMvc;

    private AnswerCounter answerCounter;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        AnswerCounterResource answerCounterResource = new AnswerCounterResource(answerCounterRepository);
        this.restAnswerCounterMockMvc = MockMvcBuilders.standaloneSetup(answerCounterResource)
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
    public static AnswerCounter createEntity(EntityManager em) {
        AnswerCounter answerCounter = new AnswerCounter()
            .counter(DEFAULT_COUNTER);
        return answerCounter;
    }

    @Before
    public void initTest() {
        answerCounter = createEntity(em);
    }

    @Test
    @Transactional
    public void createAnswerCounter() throws Exception {
        int databaseSizeBeforeCreate = answerCounterRepository.findAll().size();

        // Create the AnswerCounter
        restAnswerCounterMockMvc.perform(post("/api/answer-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(answerCounter)))
            .andExpect(status().isCreated());

        // Validate the AnswerCounter in the database
        List<AnswerCounter> answerCounterList = answerCounterRepository.findAll();
        assertThat(answerCounterList).hasSize(databaseSizeBeforeCreate + 1);
        AnswerCounter testAnswerCounter = answerCounterList.get(answerCounterList.size() - 1);
        assertThat(testAnswerCounter.getCounter()).isEqualTo(DEFAULT_COUNTER);
    }

    @Test
    @Transactional
    public void createAnswerCounterWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = answerCounterRepository.findAll().size();

        // Create the AnswerCounter with an existing ID
        answerCounter.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restAnswerCounterMockMvc.perform(post("/api/answer-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(answerCounter)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<AnswerCounter> answerCounterList = answerCounterRepository.findAll();
        assertThat(answerCounterList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllAnswerCounters() throws Exception {
        // Initialize the database
        answerCounterRepository.saveAndFlush(answerCounter);

        // Get all the answerCounterList
        restAnswerCounterMockMvc.perform(get("/api/answer-counters?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(answerCounter.getId().intValue())))
            .andExpect(jsonPath("$.[*].counter").value(hasItem(DEFAULT_COUNTER)));
    }

    @Test
    @Transactional
    public void getAnswerCounter() throws Exception {
        // Initialize the database
        answerCounterRepository.saveAndFlush(answerCounter);

        // Get the answerCounter
        restAnswerCounterMockMvc.perform(get("/api/answer-counters/{id}", answerCounter.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(answerCounter.getId().intValue()))
            .andExpect(jsonPath("$.counter").value(DEFAULT_COUNTER));
    }

    @Test
    @Transactional
    public void getNonExistingAnswerCounter() throws Exception {
        // Get the answerCounter
        restAnswerCounterMockMvc.perform(get("/api/answer-counters/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateAnswerCounter() throws Exception {
        // Initialize the database
        answerCounterRepository.saveAndFlush(answerCounter);
        int databaseSizeBeforeUpdate = answerCounterRepository.findAll().size();

        // Update the answerCounter
        AnswerCounter updatedAnswerCounter = answerCounterRepository.findOne(answerCounter.getId());
        updatedAnswerCounter
            .counter(UPDATED_COUNTER);

        restAnswerCounterMockMvc.perform(put("/api/answer-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedAnswerCounter)))
            .andExpect(status().isOk());

        // Validate the AnswerCounter in the database
        List<AnswerCounter> answerCounterList = answerCounterRepository.findAll();
        assertThat(answerCounterList).hasSize(databaseSizeBeforeUpdate);
        AnswerCounter testAnswerCounter = answerCounterList.get(answerCounterList.size() - 1);
        assertThat(testAnswerCounter.getCounter()).isEqualTo(UPDATED_COUNTER);
    }

    @Test
    @Transactional
    public void updateNonExistingAnswerCounter() throws Exception {
        int databaseSizeBeforeUpdate = answerCounterRepository.findAll().size();

        // Create the AnswerCounter

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restAnswerCounterMockMvc.perform(put("/api/answer-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(answerCounter)))
            .andExpect(status().isCreated());

        // Validate the AnswerCounter in the database
        List<AnswerCounter> answerCounterList = answerCounterRepository.findAll();
        assertThat(answerCounterList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteAnswerCounter() throws Exception {
        // Initialize the database
        answerCounterRepository.saveAndFlush(answerCounter);
        int databaseSizeBeforeDelete = answerCounterRepository.findAll().size();

        // Get the answerCounter
        restAnswerCounterMockMvc.perform(delete("/api/answer-counters/{id}", answerCounter.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<AnswerCounter> answerCounterList = answerCounterRepository.findAll();
        assertThat(answerCounterList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(AnswerCounter.class);
        AnswerCounter answerCounter1 = new AnswerCounter();
        answerCounter1.setId(1L);
        AnswerCounter answerCounter2 = new AnswerCounter();
        answerCounter2.setId(answerCounter1.getId());
        assertThat(answerCounter1).isEqualTo(answerCounter2);
        answerCounter2.setId(2L);
        assertThat(answerCounter1).isNotEqualTo(answerCounter2);
        answerCounter1.setId(null);
        assertThat(answerCounter1).isNotEqualTo(answerCounter2);
    }
}
