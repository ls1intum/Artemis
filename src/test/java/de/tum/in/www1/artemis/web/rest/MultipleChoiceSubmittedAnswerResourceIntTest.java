package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.MultipleChoiceSubmittedAnswer;
import de.tum.in.www1.artemis.repository.MultipleChoiceSubmittedAnswerRepository;
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
 * Test class for the MultipleChoiceSubmittedAnswerResource REST controller.
 *
 * @see MultipleChoiceSubmittedAnswerResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class MultipleChoiceSubmittedAnswerResourceIntTest {

    @Autowired
    private MultipleChoiceSubmittedAnswerRepository multipleChoiceSubmittedAnswerRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restMultipleChoiceSubmittedAnswerMockMvc;

    private MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final MultipleChoiceSubmittedAnswerResource multipleChoiceSubmittedAnswerResource = new MultipleChoiceSubmittedAnswerResource(multipleChoiceSubmittedAnswerRepository);
        this.restMultipleChoiceSubmittedAnswerMockMvc = MockMvcBuilders.standaloneSetup(multipleChoiceSubmittedAnswerResource)
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
    public static MultipleChoiceSubmittedAnswer createEntity(EntityManager em) {
        MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
        return multipleChoiceSubmittedAnswer;
    }

    @Before
    public void initTest() {
        multipleChoiceSubmittedAnswer = createEntity(em);
    }

    @Test
    @Transactional
    public void createMultipleChoiceSubmittedAnswer() throws Exception {
        int databaseSizeBeforeCreate = multipleChoiceSubmittedAnswerRepository.findAll().size();

        // Create the MultipleChoiceSubmittedAnswer
        restMultipleChoiceSubmittedAnswerMockMvc.perform(post("/api/multiple-choice-submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceSubmittedAnswer)))
            .andExpect(status().isCreated());

        // Validate the MultipleChoiceSubmittedAnswer in the database
        List<MultipleChoiceSubmittedAnswer> multipleChoiceSubmittedAnswerList = multipleChoiceSubmittedAnswerRepository.findAll();
        assertThat(multipleChoiceSubmittedAnswerList).hasSize(databaseSizeBeforeCreate + 1);
        MultipleChoiceSubmittedAnswer testMultipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswerList.get(multipleChoiceSubmittedAnswerList.size() - 1);
    }

    @Test
    @Transactional
    public void createMultipleChoiceSubmittedAnswerWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = multipleChoiceSubmittedAnswerRepository.findAll().size();

        // Create the MultipleChoiceSubmittedAnswer with an existing ID
        multipleChoiceSubmittedAnswer.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restMultipleChoiceSubmittedAnswerMockMvc.perform(post("/api/multiple-choice-submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceSubmittedAnswer)))
            .andExpect(status().isBadRequest());

        // Validate the MultipleChoiceSubmittedAnswer in the database
        List<MultipleChoiceSubmittedAnswer> multipleChoiceSubmittedAnswerList = multipleChoiceSubmittedAnswerRepository.findAll();
        assertThat(multipleChoiceSubmittedAnswerList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllMultipleChoiceSubmittedAnswers() throws Exception {
        // Initialize the database
        multipleChoiceSubmittedAnswerRepository.saveAndFlush(multipleChoiceSubmittedAnswer);

        // Get all the multipleChoiceSubmittedAnswerList
        restMultipleChoiceSubmittedAnswerMockMvc.perform(get("/api/multiple-choice-submitted-answers?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(multipleChoiceSubmittedAnswer.getId().intValue())));
    }

    @Test
    @Transactional
    public void getMultipleChoiceSubmittedAnswer() throws Exception {
        // Initialize the database
        multipleChoiceSubmittedAnswerRepository.saveAndFlush(multipleChoiceSubmittedAnswer);

        // Get the multipleChoiceSubmittedAnswer
        restMultipleChoiceSubmittedAnswerMockMvc.perform(get("/api/multiple-choice-submitted-answers/{id}", multipleChoiceSubmittedAnswer.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(multipleChoiceSubmittedAnswer.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingMultipleChoiceSubmittedAnswer() throws Exception {
        // Get the multipleChoiceSubmittedAnswer
        restMultipleChoiceSubmittedAnswerMockMvc.perform(get("/api/multiple-choice-submitted-answers/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateMultipleChoiceSubmittedAnswer() throws Exception {
        // Initialize the database
        multipleChoiceSubmittedAnswerRepository.saveAndFlush(multipleChoiceSubmittedAnswer);
        int databaseSizeBeforeUpdate = multipleChoiceSubmittedAnswerRepository.findAll().size();

        // Update the multipleChoiceSubmittedAnswer
        MultipleChoiceSubmittedAnswer updatedMultipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswerRepository.findOne(multipleChoiceSubmittedAnswer.getId());
        // Disconnect from session so that the updates on updatedMultipleChoiceSubmittedAnswer are not directly saved in db
        em.detach(updatedMultipleChoiceSubmittedAnswer);

        restMultipleChoiceSubmittedAnswerMockMvc.perform(put("/api/multiple-choice-submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedMultipleChoiceSubmittedAnswer)))
            .andExpect(status().isOk());

        // Validate the MultipleChoiceSubmittedAnswer in the database
        List<MultipleChoiceSubmittedAnswer> multipleChoiceSubmittedAnswerList = multipleChoiceSubmittedAnswerRepository.findAll();
        assertThat(multipleChoiceSubmittedAnswerList).hasSize(databaseSizeBeforeUpdate);
        MultipleChoiceSubmittedAnswer testMultipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswerList.get(multipleChoiceSubmittedAnswerList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingMultipleChoiceSubmittedAnswer() throws Exception {
        int databaseSizeBeforeUpdate = multipleChoiceSubmittedAnswerRepository.findAll().size();

        // Create the MultipleChoiceSubmittedAnswer

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restMultipleChoiceSubmittedAnswerMockMvc.perform(put("/api/multiple-choice-submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(multipleChoiceSubmittedAnswer)))
            .andExpect(status().isCreated());

        // Validate the MultipleChoiceSubmittedAnswer in the database
        List<MultipleChoiceSubmittedAnswer> multipleChoiceSubmittedAnswerList = multipleChoiceSubmittedAnswerRepository.findAll();
        assertThat(multipleChoiceSubmittedAnswerList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteMultipleChoiceSubmittedAnswer() throws Exception {
        // Initialize the database
        multipleChoiceSubmittedAnswerRepository.saveAndFlush(multipleChoiceSubmittedAnswer);
        int databaseSizeBeforeDelete = multipleChoiceSubmittedAnswerRepository.findAll().size();

        // Get the multipleChoiceSubmittedAnswer
        restMultipleChoiceSubmittedAnswerMockMvc.perform(delete("/api/multiple-choice-submitted-answers/{id}", multipleChoiceSubmittedAnswer.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<MultipleChoiceSubmittedAnswer> multipleChoiceSubmittedAnswerList = multipleChoiceSubmittedAnswerRepository.findAll();
        assertThat(multipleChoiceSubmittedAnswerList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(MultipleChoiceSubmittedAnswer.class);
        MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer1 = new MultipleChoiceSubmittedAnswer();
        multipleChoiceSubmittedAnswer1.setId(1L);
        MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer2 = new MultipleChoiceSubmittedAnswer();
        multipleChoiceSubmittedAnswer2.setId(multipleChoiceSubmittedAnswer1.getId());
        assertThat(multipleChoiceSubmittedAnswer1).isEqualTo(multipleChoiceSubmittedAnswer2);
        multipleChoiceSubmittedAnswer2.setId(2L);
        assertThat(multipleChoiceSubmittedAnswer1).isNotEqualTo(multipleChoiceSubmittedAnswer2);
        multipleChoiceSubmittedAnswer1.setId(null);
        assertThat(multipleChoiceSubmittedAnswer1).isNotEqualTo(multipleChoiceSubmittedAnswer2);
    }
}
