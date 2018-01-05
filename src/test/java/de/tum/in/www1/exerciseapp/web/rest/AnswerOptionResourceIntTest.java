package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;
import de.tum.in.www1.exerciseapp.domain.AnswerOption;
import de.tum.in.www1.exerciseapp.repository.AnswerOptionRepository;
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
 * Test class for the AnswerOptionResource REST controller.
 *
 * @see AnswerOptionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class AnswerOptionResourceIntTest {

    private static final String DEFAULT_TEXT = "AAAAAAAAAA";
    private static final String UPDATED_TEXT = "BBBBBBBBBB";

    private static final String DEFAULT_HINT = "AAAAAAAAAA";
    private static final String UPDATED_HINT = "BBBBBBBBBB";

    private static final String DEFAULT_EXPLANATION = "AAAAAAAAAA";
    private static final String UPDATED_EXPLANATION = "BBBBBBBBBB";

    private static final Boolean DEFAULT_IS_CORRECT = false;
    private static final Boolean UPDATED_IS_CORRECT = true;

    @Autowired
    private AnswerOptionRepository answerOptionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restAnswerOptionMockMvc;

    private AnswerOption answerOption;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        AnswerOptionResource answerOptionResource = new AnswerOptionResource(answerOptionRepository);
        this.restAnswerOptionMockMvc = MockMvcBuilders.standaloneSetup(answerOptionResource)
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
    public static AnswerOption createEntity(EntityManager em) {
        AnswerOption answerOption = new AnswerOption()
            .text(DEFAULT_TEXT)
            .hint(DEFAULT_HINT)
            .explanation(DEFAULT_EXPLANATION)
            .isCorrect(DEFAULT_IS_CORRECT);
        return answerOption;
    }

    @Before
    public void initTest() {
        answerOption = createEntity(em);
    }

    @Test
    @Transactional
    public void createAnswerOption() throws Exception {
        int databaseSizeBeforeCreate = answerOptionRepository.findAll().size();

        // Create the AnswerOption
        restAnswerOptionMockMvc.perform(post("/api/answer-options")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(answerOption)))
            .andExpect(status().isCreated());

        // Validate the AnswerOption in the database
        List<AnswerOption> answerOptionList = answerOptionRepository.findAll();
        assertThat(answerOptionList).hasSize(databaseSizeBeforeCreate + 1);
        AnswerOption testAnswerOption = answerOptionList.get(answerOptionList.size() - 1);
        assertThat(testAnswerOption.getText()).isEqualTo(DEFAULT_TEXT);
        assertThat(testAnswerOption.getHint()).isEqualTo(DEFAULT_HINT);
        assertThat(testAnswerOption.getExplanation()).isEqualTo(DEFAULT_EXPLANATION);
        assertThat(testAnswerOption.isIsCorrect()).isEqualTo(DEFAULT_IS_CORRECT);
    }

    @Test
    @Transactional
    public void createAnswerOptionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = answerOptionRepository.findAll().size();

        // Create the AnswerOption with an existing ID
        answerOption.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restAnswerOptionMockMvc.perform(post("/api/answer-options")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(answerOption)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<AnswerOption> answerOptionList = answerOptionRepository.findAll();
        assertThat(answerOptionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllAnswerOptions() throws Exception {
        // Initialize the database
        answerOptionRepository.saveAndFlush(answerOption);

        // Get all the answerOptionList
        restAnswerOptionMockMvc.perform(get("/api/answer-options?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(answerOption.getId().intValue())))
            .andExpect(jsonPath("$.[*].text").value(hasItem(DEFAULT_TEXT.toString())))
            .andExpect(jsonPath("$.[*].hint").value(hasItem(DEFAULT_HINT.toString())))
            .andExpect(jsonPath("$.[*].explanation").value(hasItem(DEFAULT_EXPLANATION.toString())))
            .andExpect(jsonPath("$.[*].isCorrect").value(hasItem(DEFAULT_IS_CORRECT.booleanValue())));
    }

    @Test
    @Transactional
    public void getAnswerOption() throws Exception {
        // Initialize the database
        answerOptionRepository.saveAndFlush(answerOption);

        // Get the answerOption
        restAnswerOptionMockMvc.perform(get("/api/answer-options/{id}", answerOption.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(answerOption.getId().intValue()))
            .andExpect(jsonPath("$.text").value(DEFAULT_TEXT.toString()))
            .andExpect(jsonPath("$.hint").value(DEFAULT_HINT.toString()))
            .andExpect(jsonPath("$.explanation").value(DEFAULT_EXPLANATION.toString()))
            .andExpect(jsonPath("$.isCorrect").value(DEFAULT_IS_CORRECT.booleanValue()));
    }

    @Test
    @Transactional
    public void getNonExistingAnswerOption() throws Exception {
        // Get the answerOption
        restAnswerOptionMockMvc.perform(get("/api/answer-options/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateAnswerOption() throws Exception {
        // Initialize the database
        answerOptionRepository.saveAndFlush(answerOption);
        int databaseSizeBeforeUpdate = answerOptionRepository.findAll().size();

        // Update the answerOption
        AnswerOption updatedAnswerOption = answerOptionRepository.findOne(answerOption.getId());
        updatedAnswerOption
            .text(UPDATED_TEXT)
            .hint(UPDATED_HINT)
            .explanation(UPDATED_EXPLANATION)
            .isCorrect(UPDATED_IS_CORRECT);

        restAnswerOptionMockMvc.perform(put("/api/answer-options")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedAnswerOption)))
            .andExpect(status().isOk());

        // Validate the AnswerOption in the database
        List<AnswerOption> answerOptionList = answerOptionRepository.findAll();
        assertThat(answerOptionList).hasSize(databaseSizeBeforeUpdate);
        AnswerOption testAnswerOption = answerOptionList.get(answerOptionList.size() - 1);
        assertThat(testAnswerOption.getText()).isEqualTo(UPDATED_TEXT);
        assertThat(testAnswerOption.getHint()).isEqualTo(UPDATED_HINT);
        assertThat(testAnswerOption.getExplanation()).isEqualTo(UPDATED_EXPLANATION);
        assertThat(testAnswerOption.isIsCorrect()).isEqualTo(UPDATED_IS_CORRECT);
    }

    @Test
    @Transactional
    public void updateNonExistingAnswerOption() throws Exception {
        int databaseSizeBeforeUpdate = answerOptionRepository.findAll().size();

        // Create the AnswerOption

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restAnswerOptionMockMvc.perform(put("/api/answer-options")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(answerOption)))
            .andExpect(status().isCreated());

        // Validate the AnswerOption in the database
        List<AnswerOption> answerOptionList = answerOptionRepository.findAll();
        assertThat(answerOptionList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteAnswerOption() throws Exception {
        // Initialize the database
        answerOptionRepository.saveAndFlush(answerOption);
        int databaseSizeBeforeDelete = answerOptionRepository.findAll().size();

        // Get the answerOption
        restAnswerOptionMockMvc.perform(delete("/api/answer-options/{id}", answerOption.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<AnswerOption> answerOptionList = answerOptionRepository.findAll();
        assertThat(answerOptionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(AnswerOption.class);
        AnswerOption answerOption1 = new AnswerOption();
        answerOption1.setId(1L);
        AnswerOption answerOption2 = new AnswerOption();
        answerOption2.setId(answerOption1.getId());
        assertThat(answerOption1).isEqualTo(answerOption2);
        answerOption2.setId(2L);
        assertThat(answerOption1).isNotEqualTo(answerOption2);
        answerOption1.setId(null);
        assertThat(answerOption1).isNotEqualTo(answerOption2);
    }
}
