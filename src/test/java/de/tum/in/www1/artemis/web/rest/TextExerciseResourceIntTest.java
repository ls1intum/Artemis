package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
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
 * Test class for the TextExerciseResource REST controller.
 *
 * @see TextExerciseResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class TextExerciseResourceIntTest {

    private static final String DEFAULT_SAMPLE_SOLUTION = "AAAAAAAAAA";
    private static final String UPDATED_SAMPLE_SOLUTION = "BBBBBBBBBB";

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restTextExerciseMockMvc;

    private TextExercise textExercise;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final TextExerciseResource textExerciseResource = new TextExerciseResource(textExerciseRepository);
        this.restTextExerciseMockMvc = MockMvcBuilders.standaloneSetup(textExerciseResource)
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
    public static TextExercise createEntity(EntityManager em) {
        TextExercise textExercise = new TextExercise()
            .sampleSolution(DEFAULT_SAMPLE_SOLUTION);
        return textExercise;
    }

    @Before
    public void initTest() {
        textExercise = createEntity(em);
    }

    @Test
    @Transactional
    public void createTextExercise() throws Exception {
        int databaseSizeBeforeCreate = textExerciseRepository.findAll().size();

        // Create the TextExercise
        restTextExerciseMockMvc.perform(post("/api/text-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(textExercise)))
            .andExpect(status().isCreated());

        // Validate the TextExercise in the database
        List<TextExercise> textExerciseList = textExerciseRepository.findAll();
        assertThat(textExerciseList).hasSize(databaseSizeBeforeCreate + 1);
        TextExercise testTextExercise = textExerciseList.get(textExerciseList.size() - 1);
        assertThat(testTextExercise.getSampleSolution()).isEqualTo(DEFAULT_SAMPLE_SOLUTION);
    }

    @Test
    @Transactional
    public void createTextExerciseWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = textExerciseRepository.findAll().size();

        // Create the TextExercise with an existing ID
        textExercise.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restTextExerciseMockMvc.perform(post("/api/text-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(textExercise)))
            .andExpect(status().isBadRequest());

        // Validate the TextExercise in the database
        List<TextExercise> textExerciseList = textExerciseRepository.findAll();
        assertThat(textExerciseList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllTextExercises() throws Exception {
        // Initialize the database
        textExerciseRepository.saveAndFlush(textExercise);

        // Get all the textExerciseList
        restTextExerciseMockMvc.perform(get("/api/text-exercises?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(textExercise.getId().intValue())))
            .andExpect(jsonPath("$.[*].sampleSolution").value(hasItem(DEFAULT_SAMPLE_SOLUTION.toString())));
    }
    
    @Test
    @Transactional
    public void getTextExercise() throws Exception {
        // Initialize the database
        textExerciseRepository.saveAndFlush(textExercise);

        // Get the textExercise
        restTextExerciseMockMvc.perform(get("/api/text-exercises/{id}", textExercise.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(textExercise.getId().intValue()))
            .andExpect(jsonPath("$.sampleSolution").value(DEFAULT_SAMPLE_SOLUTION.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingTextExercise() throws Exception {
        // Get the textExercise
        restTextExerciseMockMvc.perform(get("/api/text-exercises/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateTextExercise() throws Exception {
        // Initialize the database
        textExerciseRepository.saveAndFlush(textExercise);

        int databaseSizeBeforeUpdate = textExerciseRepository.findAll().size();

        // Update the textExercise
        TextExercise updatedTextExercise = textExerciseRepository.findById(textExercise.getId()).get();
        // Disconnect from session so that the updates on updatedTextExercise are not directly saved in db
        em.detach(updatedTextExercise);
        updatedTextExercise
            .sampleSolution(UPDATED_SAMPLE_SOLUTION);

        restTextExerciseMockMvc.perform(put("/api/text-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedTextExercise)))
            .andExpect(status().isOk());

        // Validate the TextExercise in the database
        List<TextExercise> textExerciseList = textExerciseRepository.findAll();
        assertThat(textExerciseList).hasSize(databaseSizeBeforeUpdate);
        TextExercise testTextExercise = textExerciseList.get(textExerciseList.size() - 1);
        assertThat(testTextExercise.getSampleSolution()).isEqualTo(UPDATED_SAMPLE_SOLUTION);
    }

    @Test
    @Transactional
    public void updateNonExistingTextExercise() throws Exception {
        int databaseSizeBeforeUpdate = textExerciseRepository.findAll().size();

        // Create the TextExercise

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTextExerciseMockMvc.perform(put("/api/text-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(textExercise)))
            .andExpect(status().isBadRequest());

        // Validate the TextExercise in the database
        List<TextExercise> textExerciseList = textExerciseRepository.findAll();
        assertThat(textExerciseList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteTextExercise() throws Exception {
        // Initialize the database
        textExerciseRepository.saveAndFlush(textExercise);

        int databaseSizeBeforeDelete = textExerciseRepository.findAll().size();

        // Get the textExercise
        restTextExerciseMockMvc.perform(delete("/api/text-exercises/{id}", textExercise.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<TextExercise> textExerciseList = textExerciseRepository.findAll();
        assertThat(textExerciseList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(TextExercise.class);
        TextExercise textExercise1 = new TextExercise();
        textExercise1.setId(1L);
        TextExercise textExercise2 = new TextExercise();
        textExercise2.setId(textExercise1.getId());
        assertThat(textExercise1).isEqualTo(textExercise2);
        textExercise2.setId(2L);
        assertThat(textExercise1).isNotEqualTo(textExercise2);
        textExercise1.setId(null);
        assertThat(textExercise1).isNotEqualTo(textExercise2);
    }
}
