package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;
import de.tum.in.www1.exerciseapp.domain.AnswerCounter;
import de.tum.in.www1.exerciseapp.domain.StatisticCounter;
import de.tum.in.www1.exerciseapp.repository.StatisticCounterRepository;
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
 * Test class for the StatisticCounterResource REST controller.
 *
 * @see StatisticCounterResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class StatisticCounterResourceIntTest {

    private static final Integer DEFAULT_RATED_COUNTER = 1;
    private static final Integer UPDATED_RATED_COUNTER = 2;

    private static final Integer DEFAULT_UN_RATED_COUNTER = 1;
    private static final Integer UPDATED_UN_RATED_COUNTER = 2;

    @Autowired
    private StatisticCounterRepository statisticCounterRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restStatisticCounterMockMvc;

    private StatisticCounter statisticCounter;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        StatisticCounterResource statisticCounterResource = new StatisticCounterResource(statisticCounterRepository);
        this.restStatisticCounterMockMvc = MockMvcBuilders.standaloneSetup(statisticCounterResource)
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
    public static StatisticCounter createEntity(EntityManager em) {
        StatisticCounter statisticCounter = new AnswerCounter()
            .ratedCounter(DEFAULT_RATED_COUNTER)
            .unRatedCounter(DEFAULT_UN_RATED_COUNTER);
        return statisticCounter;
    }

    @Before
    public void initTest() {
        statisticCounter = createEntity(em);
    }

    @Test
    @Transactional
    public void createStatisticCounter() throws Exception {
        int databaseSizeBeforeCreate = statisticCounterRepository.findAll().size();

        // Create the StatisticCounter
        restStatisticCounterMockMvc.perform(post("/api/statistic-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(statisticCounter)))
            .andExpect(status().isCreated());

        // Validate the StatisticCounter in the database
        List<StatisticCounter> statisticCounterList = statisticCounterRepository.findAll();
        assertThat(statisticCounterList).hasSize(databaseSizeBeforeCreate + 1);
        StatisticCounter testStatisticCounter = statisticCounterList.get(statisticCounterList.size() - 1);
        assertThat(testStatisticCounter.getRatedCounter()).isEqualTo(DEFAULT_RATED_COUNTER);
        assertThat(testStatisticCounter.getUnRatedCounter()).isEqualTo(DEFAULT_UN_RATED_COUNTER);
    }

    @Test
    @Transactional
    public void createStatisticCounterWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = statisticCounterRepository.findAll().size();

        // Create the StatisticCounter with an existing ID
        statisticCounter.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restStatisticCounterMockMvc.perform(post("/api/statistic-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(statisticCounter)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<StatisticCounter> statisticCounterList = statisticCounterRepository.findAll();
        assertThat(statisticCounterList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllStatisticCounters() throws Exception {
        // Initialize the database
        statisticCounterRepository.saveAndFlush(statisticCounter);

        // Get all the statisticCounterList
        restStatisticCounterMockMvc.perform(get("/api/statistic-counters?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(statisticCounter.getId().intValue())))
            .andExpect(jsonPath("$.[*].ratedCounter").value(hasItem(DEFAULT_RATED_COUNTER)))
            .andExpect(jsonPath("$.[*].unRatedCounter").value(hasItem(DEFAULT_UN_RATED_COUNTER)));
    }

    @Test
    @Transactional
    public void getStatisticCounter() throws Exception {
        // Initialize the database
        statisticCounterRepository.saveAndFlush(statisticCounter);

        // Get the statisticCounter
        restStatisticCounterMockMvc.perform(get("/api/statistic-counters/{id}", statisticCounter.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(statisticCounter.getId().intValue()))
            .andExpect(jsonPath("$.ratedCounter").value(DEFAULT_RATED_COUNTER))
            .andExpect(jsonPath("$.unRatedCounter").value(DEFAULT_UN_RATED_COUNTER));
    }

    @Test
    @Transactional
    public void getNonExistingStatisticCounter() throws Exception {
        // Get the statisticCounter
        restStatisticCounterMockMvc.perform(get("/api/statistic-counters/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateStatisticCounter() throws Exception {
        // Initialize the database
        statisticCounterRepository.saveAndFlush(statisticCounter);
        int databaseSizeBeforeUpdate = statisticCounterRepository.findAll().size();

        // Update the statisticCounter
        StatisticCounter updatedStatisticCounter = statisticCounterRepository.findOne(statisticCounter.getId());
        updatedStatisticCounter
            .ratedCounter(UPDATED_RATED_COUNTER)
            .unRatedCounter(UPDATED_UN_RATED_COUNTER);

        restStatisticCounterMockMvc.perform(put("/api/statistic-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedStatisticCounter)))
            .andExpect(status().isOk());

        // Validate the StatisticCounter in the database
        List<StatisticCounter> statisticCounterList = statisticCounterRepository.findAll();
        assertThat(statisticCounterList).hasSize(databaseSizeBeforeUpdate);
        StatisticCounter testStatisticCounter = statisticCounterList.get(statisticCounterList.size() - 1);
        assertThat(testStatisticCounter.getRatedCounter()).isEqualTo(UPDATED_RATED_COUNTER);
        assertThat(testStatisticCounter.getUnRatedCounter()).isEqualTo(UPDATED_UN_RATED_COUNTER);
    }

    @Test
    @Transactional
    public void updateNonExistingStatisticCounter() throws Exception {
        int databaseSizeBeforeUpdate = statisticCounterRepository.findAll().size();

        // Create the StatisticCounter

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restStatisticCounterMockMvc.perform(put("/api/statistic-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(statisticCounter)))
            .andExpect(status().isCreated());

        // Validate the StatisticCounter in the database
        List<StatisticCounter> statisticCounterList = statisticCounterRepository.findAll();
        assertThat(statisticCounterList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteStatisticCounter() throws Exception {
        // Initialize the database
        statisticCounterRepository.saveAndFlush(statisticCounter);
        int databaseSizeBeforeDelete = statisticCounterRepository.findAll().size();

        // Get the statisticCounter
        restStatisticCounterMockMvc.perform(delete("/api/statistic-counters/{id}", statisticCounter.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<StatisticCounter> statisticCounterList = statisticCounterRepository.findAll();
        assertThat(statisticCounterList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(StatisticCounter.class);
        StatisticCounter statisticCounter1 = new AnswerCounter();
        statisticCounter1.setId(1L);
        StatisticCounter statisticCounter2 = new AnswerCounter();
        statisticCounter2.setId(statisticCounter1.getId());
        assertThat(statisticCounter1).isEqualTo(statisticCounter2);
        statisticCounter2.setId(2L);
        assertThat(statisticCounter1).isNotEqualTo(statisticCounter2);
        statisticCounter1.setId(null);
        assertThat(statisticCounter1).isNotEqualTo(statisticCounter2);
    }
}
