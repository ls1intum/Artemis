package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.MultipleChoiceStatistic;
import de.tum.in.www1.exerciseapp.repository.MultipleChoiceStatisticRepository;
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
 * Test class for the MultipleChoiceStatisticResource REST controller.
 *
 * @see MultipleChoiceStatisticResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class MultipleChoiceStatisticResourceIntTest {

    @Autowired
    private MultipleChoiceStatisticRepository multipleChoiceStatisticRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restMultipleChoiceStatisticMockMvc;

    private MultipleChoiceStatistic multipleChoiceStatistic;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        MultipleChoiceStatisticResource multipleChoiceStatisticResource = new MultipleChoiceStatisticResource(multipleChoiceStatisticRepository);
        this.restMultipleChoiceStatisticMockMvc = MockMvcBuilders.standaloneSetup(multipleChoiceStatisticResource)
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
    public static MultipleChoiceStatistic createEntity(EntityManager em) {
        MultipleChoiceStatistic multipleChoiceStatistic = new MultipleChoiceStatistic();
        return multipleChoiceStatistic;
    }

    @Before
    public void initTest() {
        multipleChoiceStatistic = createEntity(em);
    }

    @Test
    @Transactional
    public void createMultipleChoiceStatistic() throws Exception {
        int databaseSizeBeforeCreate = multipleChoiceStatisticRepository.findAll().size();

        // Create the MultipleChoiceStatistic
        restMultipleChoiceStatisticMockMvc.perform(post("/api/multiple-choice-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceStatistic)))
            .andExpect(status().isCreated());

        // Validate the MultipleChoiceStatistic in the database
        List<MultipleChoiceStatistic> multipleChoiceStatisticList = multipleChoiceStatisticRepository.findAll();
        assertThat(multipleChoiceStatisticList).hasSize(databaseSizeBeforeCreate + 1);
        MultipleChoiceStatistic testMultipleChoiceStatistic = multipleChoiceStatisticList.get(multipleChoiceStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void createMultipleChoiceStatisticWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = multipleChoiceStatisticRepository.findAll().size();

        // Create the MultipleChoiceStatistic with an existing ID
        multipleChoiceStatistic.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restMultipleChoiceStatisticMockMvc.perform(post("/api/multiple-choice-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceStatistic)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<MultipleChoiceStatistic> multipleChoiceStatisticList = multipleChoiceStatisticRepository.findAll();
        assertThat(multipleChoiceStatisticList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllMultipleChoiceStatistics() throws Exception {
        // Initialize the database
        multipleChoiceStatisticRepository.saveAndFlush(multipleChoiceStatistic);

        // Get all the multipleChoiceStatisticList
        restMultipleChoiceStatisticMockMvc.perform(get("/api/multiple-choice-statistics?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(multipleChoiceStatistic.getId().intValue())));
    }

    @Test
    @Transactional
    public void getMultipleChoiceStatistic() throws Exception {
        // Initialize the database
        multipleChoiceStatisticRepository.saveAndFlush(multipleChoiceStatistic);

        // Get the multipleChoiceStatistic
        restMultipleChoiceStatisticMockMvc.perform(get("/api/multiple-choice-statistics/{id}", multipleChoiceStatistic.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(multipleChoiceStatistic.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingMultipleChoiceStatistic() throws Exception {
        // Get the multipleChoiceStatistic
        restMultipleChoiceStatisticMockMvc.perform(get("/api/multiple-choice-statistics/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateMultipleChoiceStatistic() throws Exception {
        // Initialize the database
        multipleChoiceStatisticRepository.saveAndFlush(multipleChoiceStatistic);
        int databaseSizeBeforeUpdate = multipleChoiceStatisticRepository.findAll().size();

        // Update the multipleChoiceStatistic
        MultipleChoiceStatistic updatedMultipleChoiceStatistic = multipleChoiceStatisticRepository.findOne(multipleChoiceStatistic.getId());

        restMultipleChoiceStatisticMockMvc.perform(put("/api/multiple-choice-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedMultipleChoiceStatistic)))
            .andExpect(status().isOk());

        // Validate the MultipleChoiceStatistic in the database
        List<MultipleChoiceStatistic> multipleChoiceStatisticList = multipleChoiceStatisticRepository.findAll();
        assertThat(multipleChoiceStatisticList).hasSize(databaseSizeBeforeUpdate);
        MultipleChoiceStatistic testMultipleChoiceStatistic = multipleChoiceStatisticList.get(multipleChoiceStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingMultipleChoiceStatistic() throws Exception {
        int databaseSizeBeforeUpdate = multipleChoiceStatisticRepository.findAll().size();

        // Create the MultipleChoiceStatistic

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restMultipleChoiceStatisticMockMvc.perform(put("/api/multiple-choice-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceStatistic)))
            .andExpect(status().isCreated());

        // Validate the MultipleChoiceStatistic in the database
        List<MultipleChoiceStatistic> multipleChoiceStatisticList = multipleChoiceStatisticRepository.findAll();
        assertThat(multipleChoiceStatisticList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteMultipleChoiceStatistic() throws Exception {
        // Initialize the database
        multipleChoiceStatisticRepository.saveAndFlush(multipleChoiceStatistic);
        int databaseSizeBeforeDelete = multipleChoiceStatisticRepository.findAll().size();

        // Get the multipleChoiceStatistic
        restMultipleChoiceStatisticMockMvc.perform(delete("/api/multiple-choice-statistics/{id}", multipleChoiceStatistic.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<MultipleChoiceStatistic> multipleChoiceStatisticList = multipleChoiceStatisticRepository.findAll();
        assertThat(multipleChoiceStatisticList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(MultipleChoiceStatistic.class);
        MultipleChoiceStatistic multipleChoiceStatistic1 = new MultipleChoiceStatistic();
        multipleChoiceStatistic1.setId(1L);
        MultipleChoiceStatistic multipleChoiceStatistic2 = new MultipleChoiceStatistic();
        multipleChoiceStatistic2.setId(multipleChoiceStatistic1.getId());
        assertThat(multipleChoiceStatistic1).isEqualTo(multipleChoiceStatistic2);
        multipleChoiceStatistic2.setId(2L);
        assertThat(multipleChoiceStatistic1).isNotEqualTo(multipleChoiceStatistic2);
        multipleChoiceStatistic1.setId(null);
        assertThat(multipleChoiceStatistic1).isNotEqualTo(multipleChoiceStatistic2);
    }
}
