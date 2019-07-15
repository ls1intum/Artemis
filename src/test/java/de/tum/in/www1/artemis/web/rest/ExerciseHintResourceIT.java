package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import javax.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;

import de.tum.in.www1.artemis.ArtemisApp;
import de.tum.in.www1.artemis.domain.ExerciseHint;
import de.tum.in.www1.artemis.repository.ExerciseHintRepository;
import de.tum.in.www1.artemis.service.ExerciseHintService;
import de.tum.in.www1.artemis.web.rest.errors.ExceptionTranslator;

/**
 * Integration tests for the {@Link ExerciseHintResource} REST controller.
 */
@SpringBootTest(classes = ArtemisApp.class)
public class ExerciseHintResourceIT {

    private static final String DEFAULT_TITLE = "AAAAAAAAAA";

    private static final String UPDATED_TITLE = "BBBBBBBBBB";

    private static final String DEFAULT_CONTENT = "AAAAAAAAAA";

    private static final String UPDATED_CONTENT = "BBBBBBBBBB";

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @Autowired
    private ExerciseHintService exerciseHintService;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    @Autowired
    private Validator validator;

    private MockMvc restExerciseHintMockMvc;

    private ExerciseHint exerciseHint;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final ExerciseHintResource exerciseHintResource = new ExerciseHintResource(exerciseHintService);
        this.restExerciseHintMockMvc = MockMvcBuilders.standaloneSetup(exerciseHintResource).setCustomArgumentResolvers(pageableArgumentResolver)
                .setControllerAdvice(exceptionTranslator).setConversionService(createFormattingConversionService()).setMessageConverters(jacksonMessageConverter)
                .setValidator(validator).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ExerciseHint createEntity(EntityManager em) {
        ExerciseHint exerciseHint = new ExerciseHint().title(DEFAULT_TITLE).content(DEFAULT_CONTENT);
        return exerciseHint;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ExerciseHint createUpdatedEntity(EntityManager em) {
        ExerciseHint exerciseHint = new ExerciseHint().title(UPDATED_TITLE).content(UPDATED_CONTENT);
        return exerciseHint;
    }

    @BeforeEach
    public void initTest() {
        exerciseHint = createEntity(em);
    }

    @Test
    @Transactional
    public void createExerciseHint() throws Exception {
        int databaseSizeBeforeCreate = exerciseHintRepository.findAll().size();

        // Create the ExerciseHint
        restExerciseHintMockMvc.perform(post("/api/exercise-hints").contentType(TestUtil.APPLICATION_JSON_UTF8).content(TestUtil.convertObjectToJsonBytes(exerciseHint)))
                .andExpect(status().isCreated());

        // Validate the ExerciseHint in the database
        List<ExerciseHint> exerciseHintList = exerciseHintRepository.findAll();
        assertThat(exerciseHintList).hasSize(databaseSizeBeforeCreate + 1);
        ExerciseHint testExerciseHint = exerciseHintList.get(exerciseHintList.size() - 1);
        assertThat(testExerciseHint.getTitle()).isEqualTo(DEFAULT_TITLE);
        assertThat(testExerciseHint.getContent()).isEqualTo(DEFAULT_CONTENT);
    }

    @Test
    @Transactional
    public void createExerciseHintWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = exerciseHintRepository.findAll().size();

        // Create the ExerciseHint with an existing ID
        exerciseHint.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restExerciseHintMockMvc.perform(post("/api/exercise-hints").contentType(TestUtil.APPLICATION_JSON_UTF8).content(TestUtil.convertObjectToJsonBytes(exerciseHint)))
                .andExpect(status().isBadRequest());

        // Validate the ExerciseHint in the database
        List<ExerciseHint> exerciseHintList = exerciseHintRepository.findAll();
        assertThat(exerciseHintList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllExerciseHints() throws Exception {
        // Initialize the database
        exerciseHintRepository.saveAndFlush(exerciseHint);

        // Get all the exerciseHintList
        restExerciseHintMockMvc.perform(get("/api/exercise-hints?sort=id,desc")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath("$.[*].id").value(hasItem(exerciseHint.getId().intValue()))).andExpect(jsonPath("$.[*].title").value(hasItem(DEFAULT_TITLE.toString())))
                .andExpect(jsonPath("$.[*].content").value(hasItem(DEFAULT_CONTENT.toString())));
    }

    @Test
    @Transactional
    public void getExerciseHint() throws Exception {
        // Initialize the database
        exerciseHintRepository.saveAndFlush(exerciseHint);

        // Get the exerciseHint
        restExerciseHintMockMvc.perform(get("/api/exercise-hints/{id}", exerciseHint.getId())).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)).andExpect(jsonPath("$.id").value(exerciseHint.getId().intValue()))
                .andExpect(jsonPath("$.title").value(DEFAULT_TITLE.toString())).andExpect(jsonPath("$.content").value(DEFAULT_CONTENT.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingExerciseHint() throws Exception {
        // Get the exerciseHint
        restExerciseHintMockMvc.perform(get("/api/exercise-hints/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateExerciseHint() throws Exception {
        // Initialize the database
        exerciseHintService.save(exerciseHint);

        int databaseSizeBeforeUpdate = exerciseHintRepository.findAll().size();

        // Update the exerciseHint
        ExerciseHint updatedExerciseHint = exerciseHintRepository.findById(exerciseHint.getId()).get();
        // Disconnect from session so that the updates on updatedExerciseHint are not directly saved in db
        em.detach(updatedExerciseHint);
        updatedExerciseHint.title(UPDATED_TITLE).content(UPDATED_CONTENT);

        restExerciseHintMockMvc.perform(put("/api/exercise-hints").contentType(TestUtil.APPLICATION_JSON_UTF8).content(TestUtil.convertObjectToJsonBytes(updatedExerciseHint)))
                .andExpect(status().isOk());

        // Validate the ExerciseHint in the database
        List<ExerciseHint> exerciseHintList = exerciseHintRepository.findAll();
        assertThat(exerciseHintList).hasSize(databaseSizeBeforeUpdate);
        ExerciseHint testExerciseHint = exerciseHintList.get(exerciseHintList.size() - 1);
        assertThat(testExerciseHint.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testExerciseHint.getContent()).isEqualTo(UPDATED_CONTENT);
    }

    @Test
    @Transactional
    public void updateNonExistingExerciseHint() throws Exception {
        int databaseSizeBeforeUpdate = exerciseHintRepository.findAll().size();

        // Create the ExerciseHint

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restExerciseHintMockMvc.perform(put("/api/exercise-hints").contentType(TestUtil.APPLICATION_JSON_UTF8).content(TestUtil.convertObjectToJsonBytes(exerciseHint)))
                .andExpect(status().isBadRequest());

        // Validate the ExerciseHint in the database
        List<ExerciseHint> exerciseHintList = exerciseHintRepository.findAll();
        assertThat(exerciseHintList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteExerciseHint() throws Exception {
        // Initialize the database
        exerciseHintService.save(exerciseHint);

        int databaseSizeBeforeDelete = exerciseHintRepository.findAll().size();

        // Delete the exerciseHint
        restExerciseHintMockMvc.perform(delete("/api/exercise-hints/{id}", exerciseHint.getId()).accept(TestUtil.APPLICATION_JSON_UTF8)).andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<ExerciseHint> exerciseHintList = exerciseHintRepository.findAll();
        assertThat(exerciseHintList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ExerciseHint.class);
        ExerciseHint exerciseHint1 = new ExerciseHint();
        exerciseHint1.setId(1L);
        ExerciseHint exerciseHint2 = new ExerciseHint();
        exerciseHint2.setId(exerciseHint1.getId());
        assertThat(exerciseHint1).isEqualTo(exerciseHint2);
        exerciseHint2.setId(2L);
        assertThat(exerciseHint1).isNotEqualTo(exerciseHint2);
        exerciseHint1.setId(null);
        assertThat(exerciseHint1).isNotEqualTo(exerciseHint2);
    }
}
