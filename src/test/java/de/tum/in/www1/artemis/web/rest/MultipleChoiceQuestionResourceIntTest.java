package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.MultipleChoiceQuestion;
import de.tum.in.www1.artemis.repository.MultipleChoiceQuestionRepository;
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
 * Test class for the MultipleChoiceQuestionResource REST controller.
 *
 * @see MultipleChoiceQuestionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class MultipleChoiceQuestionResourceIntTest {

    @Autowired
    private MultipleChoiceQuestionRepository multipleChoiceQuestionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restMultipleChoiceQuestionMockMvc;

    private MultipleChoiceQuestion multipleChoiceQuestion;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final MultipleChoiceQuestionResource multipleChoiceQuestionResource = new MultipleChoiceQuestionResource(multipleChoiceQuestionRepository);
        this.restMultipleChoiceQuestionMockMvc = MockMvcBuilders.standaloneSetup(multipleChoiceQuestionResource)
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
    public static MultipleChoiceQuestion createEntity(EntityManager em) {
        MultipleChoiceQuestion multipleChoiceQuestion = new MultipleChoiceQuestion();
        return multipleChoiceQuestion;
    }

    @Before
    public void initTest() {
        multipleChoiceQuestion = createEntity(em);
    }

    @Test
    @Transactional
    public void createMultipleChoiceQuestion() throws Exception {
        int databaseSizeBeforeCreate = multipleChoiceQuestionRepository.findAll().size();

        // Create the MultipleChoiceQuestion
        restMultipleChoiceQuestionMockMvc.perform(post("/api/multiple-choice-questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceQuestion)))
            .andExpect(status().isCreated());

        // Validate the MultipleChoiceQuestion in the database
        List<MultipleChoiceQuestion> multipleChoiceQuestionList = multipleChoiceQuestionRepository.findAll();
        assertThat(multipleChoiceQuestionList).hasSize(databaseSizeBeforeCreate + 1);
        MultipleChoiceQuestion testMultipleChoiceQuestion = multipleChoiceQuestionList.get(multipleChoiceQuestionList.size() - 1);
    }

    @Test
    @Transactional
    public void createMultipleChoiceQuestionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = multipleChoiceQuestionRepository.findAll().size();

        // Create the MultipleChoiceQuestion with an existing ID
        multipleChoiceQuestion.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restMultipleChoiceQuestionMockMvc.perform(post("/api/multiple-choice-questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceQuestion)))
            .andExpect(status().isBadRequest());

        // Validate the MultipleChoiceQuestion in the database
        List<MultipleChoiceQuestion> multipleChoiceQuestionList = multipleChoiceQuestionRepository.findAll();
        assertThat(multipleChoiceQuestionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllMultipleChoiceQuestions() throws Exception {
        // Initialize the database
        multipleChoiceQuestionRepository.saveAndFlush(multipleChoiceQuestion);

        // Get all the multipleChoiceQuestionList
        restMultipleChoiceQuestionMockMvc.perform(get("/api/multiple-choice-questions?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(multipleChoiceQuestion.getId().intValue())));
    }
    
    @Test
    @Transactional
    public void getMultipleChoiceQuestion() throws Exception {
        // Initialize the database
        multipleChoiceQuestionRepository.saveAndFlush(multipleChoiceQuestion);

        // Get the multipleChoiceQuestion
        restMultipleChoiceQuestionMockMvc.perform(get("/api/multiple-choice-questions/{id}", multipleChoiceQuestion.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(multipleChoiceQuestion.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingMultipleChoiceQuestion() throws Exception {
        // Get the multipleChoiceQuestion
        restMultipleChoiceQuestionMockMvc.perform(get("/api/multiple-choice-questions/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateMultipleChoiceQuestion() throws Exception {
        // Initialize the database
        multipleChoiceQuestionRepository.saveAndFlush(multipleChoiceQuestion);

        int databaseSizeBeforeUpdate = multipleChoiceQuestionRepository.findAll().size();

        // Update the multipleChoiceQuestion
        MultipleChoiceQuestion updatedMultipleChoiceQuestion = multipleChoiceQuestionRepository.findById(multipleChoiceQuestion.getId()).get();
        // Disconnect from session so that the updates on updatedMultipleChoiceQuestion are not directly saved in db
        em.detach(updatedMultipleChoiceQuestion);

        restMultipleChoiceQuestionMockMvc.perform(put("/api/multiple-choice-questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedMultipleChoiceQuestion)))
            .andExpect(status().isOk());

        // Validate the MultipleChoiceQuestion in the database
        List<MultipleChoiceQuestion> multipleChoiceQuestionList = multipleChoiceQuestionRepository.findAll();
        assertThat(multipleChoiceQuestionList).hasSize(databaseSizeBeforeUpdate);
        MultipleChoiceQuestion testMultipleChoiceQuestion = multipleChoiceQuestionList.get(multipleChoiceQuestionList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingMultipleChoiceQuestion() throws Exception {
        int databaseSizeBeforeUpdate = multipleChoiceQuestionRepository.findAll().size();

        // Create the MultipleChoiceQuestion

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMultipleChoiceQuestionMockMvc.perform(put("/api/multiple-choice-questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceQuestion)))
            .andExpect(status().isBadRequest());

        // Validate the MultipleChoiceQuestion in the database
        List<MultipleChoiceQuestion> multipleChoiceQuestionList = multipleChoiceQuestionRepository.findAll();
        assertThat(multipleChoiceQuestionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteMultipleChoiceQuestion() throws Exception {
        // Initialize the database
        multipleChoiceQuestionRepository.saveAndFlush(multipleChoiceQuestion);

        int databaseSizeBeforeDelete = multipleChoiceQuestionRepository.findAll().size();

        // Get the multipleChoiceQuestion
        restMultipleChoiceQuestionMockMvc.perform(delete("/api/multiple-choice-questions/{id}", multipleChoiceQuestion.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<MultipleChoiceQuestion> multipleChoiceQuestionList = multipleChoiceQuestionRepository.findAll();
        assertThat(multipleChoiceQuestionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(MultipleChoiceQuestion.class);
        MultipleChoiceQuestion multipleChoiceQuestion1 = new MultipleChoiceQuestion();
        multipleChoiceQuestion1.setId(1L);
        MultipleChoiceQuestion multipleChoiceQuestion2 = new MultipleChoiceQuestion();
        multipleChoiceQuestion2.setId(multipleChoiceQuestion1.getId());
        assertThat(multipleChoiceQuestion1).isEqualTo(multipleChoiceQuestion2);
        multipleChoiceQuestion2.setId(2L);
        assertThat(multipleChoiceQuestion1).isNotEqualTo(multipleChoiceQuestion2);
        multipleChoiceQuestion1.setId(null);
        assertThat(multipleChoiceQuestion1).isNotEqualTo(multipleChoiceQuestion2);
    }
}
