package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.MultipleChoiceStatistic;
import de.tum.in.www1.exerciseapp.domain.Statistic;
import de.tum.in.www1.exerciseapp.repository.StatisticRepository;
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
 * Test class for the StatisticResource REST controller.
 *
 * @see StatisticResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class StatisticResourceIntTest {

    private static final Boolean DEFAULT_RELEASED = false;
    private static final Boolean UPDATED_RELEASED = true;

    private static final Integer DEFAULT_PARTICIPANTS_RATED = 1;
    private static final Integer UPDATED_PARTICIPANTS_RATED = 2;

    private static final Integer DEFAULT_PARTICIPANTS_UNRATED = 1;
    private static final Integer UPDATED_PARTICIPANTS_UNRATED = 2;

    @Autowired
    private StatisticRepository statisticRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restStatisticMockMvc;

    private Statistic statistic;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        StatisticResource statisticResource = new StatisticResource(statisticRepository);
        this.restStatisticMockMvc = MockMvcBuilders.standaloneSetup(statisticResource)
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
    public static Statistic createEntity(EntityManager em) {
        Statistic statistic = new MultipleChoiceStatistic()
            .released(DEFAULT_RELEASED)
            .participantsRated(DEFAULT_PARTICIPANTS_RATED)
            .participantsUnrated(DEFAULT_PARTICIPANTS_UNRATED);
        return statistic;
    }

    @Before
    public void initTest() {
        statistic = createEntity(em);
    }

    @Test
    @Transactional
    public void createStatistic() throws Exception {
        int databaseSizeBeforeCreate = statisticRepository.findAll().size();

        // Create the Statistic
        restStatisticMockMvc.perform(post("/api/statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(statistic)))
            .andExpect(status().isCreated());

        // Validate the Statistic in the database
        List<Statistic> statisticList = statisticRepository.findAll();
        assertThat(statisticList).hasSize(databaseSizeBeforeCreate + 1);
        Statistic testStatistic = statisticList.get(statisticList.size() - 1);
        assertThat(testStatistic.isReleased()).isEqualTo(DEFAULT_RELEASED);
        assertThat(testStatistic.getParticipantsRated()).isEqualTo(DEFAULT_PARTICIPANTS_RATED);
        assertThat(testStatistic.getParticipantsUnrated()).isEqualTo(DEFAULT_PARTICIPANTS_UNRATED);
    }

    @Test
    @Transactional
    public void createStatisticWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = statisticRepository.findAll().size();

        // Create the Statistic with an existing ID
        statistic.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restStatisticMockMvc.perform(post("/api/statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(statistic)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<Statistic> statisticList = statisticRepository.findAll();
        assertThat(statisticList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllStatistics() throws Exception {
        // Initialize the database
        statisticRepository.saveAndFlush(statistic);

        // Get all the statisticList
        restStatisticMockMvc.perform(get("/api/statistics?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(statistic.getId().intValue())))
            .andExpect(jsonPath("$.[*].released").value(hasItem(DEFAULT_RELEASED.booleanValue())))
            .andExpect(jsonPath("$.[*].participantsRated").value(hasItem(DEFAULT_PARTICIPANTS_RATED)))
            .andExpect(jsonPath("$.[*].participantsUnrated").value(hasItem(DEFAULT_PARTICIPANTS_UNRATED)));
    }

    @Test
    @Transactional
    public void getStatistic() throws Exception {
        // Initialize the database
        statisticRepository.saveAndFlush(statistic);

        // Get the statistic
        restStatisticMockMvc.perform(get("/api/statistics/{id}", statistic.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(statistic.getId().intValue()))
            .andExpect(jsonPath("$.released").value(DEFAULT_RELEASED.booleanValue()))
            .andExpect(jsonPath("$.participantsRated").value(DEFAULT_PARTICIPANTS_RATED))
            .andExpect(jsonPath("$.participantsUnrated").value(DEFAULT_PARTICIPANTS_UNRATED));
    }

    @Test
    @Transactional
    public void getNonExistingStatistic() throws Exception {
        // Get the statistic
        restStatisticMockMvc.perform(get("/api/statistics/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateStatistic() throws Exception {
        // Initialize the database
        statisticRepository.saveAndFlush(statistic);
        int databaseSizeBeforeUpdate = statisticRepository.findAll().size();

        // Update the statistic
        Statistic updatedStatistic = statisticRepository.findOne(statistic.getId());
        updatedStatistic
            .released(UPDATED_RELEASED)
            .participantsRated(UPDATED_PARTICIPANTS_RATED)
            .participantsUnrated(UPDATED_PARTICIPANTS_UNRATED);

        restStatisticMockMvc.perform(put("/api/statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedStatistic)))
            .andExpect(status().isOk());

        // Validate the Statistic in the database
        List<Statistic> statisticList = statisticRepository.findAll();
        assertThat(statisticList).hasSize(databaseSizeBeforeUpdate);
        Statistic testStatistic = statisticList.get(statisticList.size() - 1);
        assertThat(testStatistic.isReleased()).isEqualTo(UPDATED_RELEASED);
        assertThat(testStatistic.getParticipantsRated()).isEqualTo(UPDATED_PARTICIPANTS_RATED);
        assertThat(testStatistic.getParticipantsUnrated()).isEqualTo(UPDATED_PARTICIPANTS_UNRATED);
    }

    @Test
    @Transactional
    public void updateNonExistingStatistic() throws Exception {
        int databaseSizeBeforeUpdate = statisticRepository.findAll().size();

        // Create the Statistic

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restStatisticMockMvc.perform(put("/api/statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(statistic)))
            .andExpect(status().isCreated());

        // Validate the Statistic in the database
        List<Statistic> statisticList = statisticRepository.findAll();
        assertThat(statisticList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteStatistic() throws Exception {
        // Initialize the database
        statisticRepository.saveAndFlush(statistic);
        int databaseSizeBeforeDelete = statisticRepository.findAll().size();

        // Get the statistic
        restStatisticMockMvc.perform(delete("/api/statistics/{id}", statistic.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<Statistic> statisticList = statisticRepository.findAll();
        assertThat(statisticList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Statistic.class);
        Statistic statistic1 = new MultipleChoiceStatistic();
        statistic1.setId(1L);
        Statistic statistic2 = new MultipleChoiceStatistic();
        statistic2.setId(statistic1.getId());
        assertThat(statistic1).isEqualTo(statistic2);
        statistic2.setId(2L);
        assertThat(statistic1).isNotEqualTo(statistic2);
        statistic1.setId(null);
        assertThat(statistic1).isNotEqualTo(statistic2);
    }
}
