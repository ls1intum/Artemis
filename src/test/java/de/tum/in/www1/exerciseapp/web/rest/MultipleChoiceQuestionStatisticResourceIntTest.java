package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;
import de.tum.in.www1.exerciseapp.domain.MultipleChoiceQuestionStatistic;
import de.tum.in.www1.exerciseapp.repository.MultipleChoiceQuestionStatisticRepository;
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
 * Test class for the MultipleChoiceQuestionStatisticResource REST controller.
 *
 * @see MultipleChoiceQuestionStatisticResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class MultipleChoiceQuestionStatisticResourceIntTest {

    @Autowired
    private MultipleChoiceQuestionStatisticRepository multipleChoiceQuestionStatisticRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restMultipleChoiceQuestionStatisticMockMvc;

    private MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        MultipleChoiceQuestionStatisticResource multipleChoiceQuestionStatisticResource = new MultipleChoiceQuestionStatisticResource(multipleChoiceQuestionStatisticRepository);
        this.restMultipleChoiceQuestionStatisticMockMvc = MockMvcBuilders.standaloneSetup(multipleChoiceQuestionStatisticResource)
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
    public static MultipleChoiceQuestionStatistic createEntity(EntityManager em) {
        MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic = new MultipleChoiceQuestionStatistic();
        return multipleChoiceQuestionStatistic;
    }

    @Before
    public void initTest() {
        multipleChoiceQuestionStatistic = createEntity(em);
    }

    @Test
    @Transactional
    public void createMultipleChoiceQuestionStatistic() throws Exception {
        int databaseSizeBeforeCreate = multipleChoiceQuestionStatisticRepository.findAll().size();

        // Create the MultipleChoiceQuestionStatistic
        restMultipleChoiceQuestionStatisticMockMvc.perform(post("/api/multiple-choice-question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceQuestionStatistic)))
            .andExpect(status().isCreated());

        // Validate the MultipleChoiceQuestionStatistic in the database
        List<MultipleChoiceQuestionStatistic> multipleChoiceQuestionStatisticList = multipleChoiceQuestionStatisticRepository.findAll();
        assertThat(multipleChoiceQuestionStatisticList).hasSize(databaseSizeBeforeCreate + 1);
        MultipleChoiceQuestionStatistic testMultipleChoiceQuestionStatistic = multipleChoiceQuestionStatisticList.get(multipleChoiceQuestionStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void createMultipleChoiceQuestionStatisticWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = multipleChoiceQuestionStatisticRepository.findAll().size();

        // Create the MultipleChoiceQuestionStatistic with an existing ID
        multipleChoiceQuestionStatistic.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restMultipleChoiceQuestionStatisticMockMvc.perform(post("/api/multiple-choice-question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceQuestionStatistic)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<MultipleChoiceQuestionStatistic> multipleChoiceQuestionStatisticList = multipleChoiceQuestionStatisticRepository.findAll();
        assertThat(multipleChoiceQuestionStatisticList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllMultipleChoiceQuestionStatistics() throws Exception {
        // Initialize the database
        multipleChoiceQuestionStatisticRepository.saveAndFlush(multipleChoiceQuestionStatistic);

        // Get all the multipleChoiceQuestionStatisticList
        restMultipleChoiceQuestionStatisticMockMvc.perform(get("/api/multiple-choice-question-statistics?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(multipleChoiceQuestionStatistic.getId().intValue())));
    }

    @Test
    @Transactional
    public void getMultipleChoiceQuestionStatistic() throws Exception {
        // Initialize the database
        multipleChoiceQuestionStatisticRepository.saveAndFlush(multipleChoiceQuestionStatistic);

        // Get the multipleChoiceQuestionStatistic
        restMultipleChoiceQuestionStatisticMockMvc.perform(get("/api/multiple-choice-question-statistics/{id}", multipleChoiceQuestionStatistic.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(multipleChoiceQuestionStatistic.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingMultipleChoiceQuestionStatistic() throws Exception {
        // Get the multipleChoiceQuestionStatistic
        restMultipleChoiceQuestionStatisticMockMvc.perform(get("/api/multiple-choice-question-statistics/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateMultipleChoiceQuestionStatistic() throws Exception {
        // Initialize the database
        multipleChoiceQuestionStatisticRepository.saveAndFlush(multipleChoiceQuestionStatistic);
        int databaseSizeBeforeUpdate = multipleChoiceQuestionStatisticRepository.findAll().size();

        // Update the multipleChoiceQuestionStatistic
        MultipleChoiceQuestionStatistic updatedMultipleChoiceQuestionStatistic = multipleChoiceQuestionStatisticRepository.findOne(multipleChoiceQuestionStatistic.getId());

        restMultipleChoiceQuestionStatisticMockMvc.perform(put("/api/multiple-choice-question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedMultipleChoiceQuestionStatistic)))
            .andExpect(status().isOk());

        // Validate the MultipleChoiceQuestionStatistic in the database
        List<MultipleChoiceQuestionStatistic> multipleChoiceQuestionStatisticList = multipleChoiceQuestionStatisticRepository.findAll();
        assertThat(multipleChoiceQuestionStatisticList).hasSize(databaseSizeBeforeUpdate);
        MultipleChoiceQuestionStatistic testMultipleChoiceQuestionStatistic = multipleChoiceQuestionStatisticList.get(multipleChoiceQuestionStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingMultipleChoiceQuestionStatistic() throws Exception {
        int databaseSizeBeforeUpdate = multipleChoiceQuestionStatisticRepository.findAll().size();

        // Create the MultipleChoiceQuestionStatistic

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restMultipleChoiceQuestionStatisticMockMvc.perform(put("/api/multiple-choice-question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceQuestionStatistic)))
            .andExpect(status().isCreated());

        // Validate the MultipleChoiceQuestionStatistic in the database
        List<MultipleChoiceQuestionStatistic> multipleChoiceQuestionStatisticList = multipleChoiceQuestionStatisticRepository.findAll();
        assertThat(multipleChoiceQuestionStatisticList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteMultipleChoiceQuestionStatistic() throws Exception {
        // Initialize the database
        multipleChoiceQuestionStatisticRepository.saveAndFlush(multipleChoiceQuestionStatistic);
        int databaseSizeBeforeDelete = multipleChoiceQuestionStatisticRepository.findAll().size();

        // Get the multipleChoiceQuestionStatistic
        restMultipleChoiceQuestionStatisticMockMvc.perform(delete("/api/multiple-choice-question-statistics/{id}", multipleChoiceQuestionStatistic.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<MultipleChoiceQuestionStatistic> multipleChoiceQuestionStatisticList = multipleChoiceQuestionStatisticRepository.findAll();
        assertThat(multipleChoiceQuestionStatisticList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(MultipleChoiceQuestionStatistic.class);
        MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic1 = new MultipleChoiceQuestionStatistic();
        multipleChoiceQuestionStatistic1.setId(1L);
        MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic2 = new MultipleChoiceQuestionStatistic();
        multipleChoiceQuestionStatistic2.setId(multipleChoiceQuestionStatistic1.getId());
        assertThat(multipleChoiceQuestionStatistic1).isEqualTo(multipleChoiceQuestionStatistic2);
        multipleChoiceQuestionStatistic2.setId(2L);
        assertThat(multipleChoiceQuestionStatistic1).isNotEqualTo(multipleChoiceQuestionStatistic2);
        multipleChoiceQuestionStatistic1.setId(null);
        assertThat(multipleChoiceQuestionStatistic1).isNotEqualTo(multipleChoiceQuestionStatistic2);
    }
}
