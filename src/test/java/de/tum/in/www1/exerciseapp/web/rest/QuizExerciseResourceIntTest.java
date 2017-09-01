package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.QuizExercise;
import de.tum.in.www1.exerciseapp.repository.QuizExerciseRepository;
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
 * Test class for the QuizExerciseResource REST controller.
 *
 * @see QuizExerciseResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class QuizExerciseResourceIntTest {

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restQuizExerciseMockMvc;

    private QuizExercise quizExercise;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final QuizExerciseResource quizExerciseResource = new QuizExerciseResource(quizExerciseRepository);
        this.restQuizExerciseMockMvc = MockMvcBuilders.standaloneSetup(quizExerciseResource)
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
    public static QuizExercise createEntity(EntityManager em) {
        QuizExercise quizExercise = new QuizExercise();
        return quizExercise;
    }

    @Before
    public void initTest() {
        quizExercise = createEntity(em);
    }

    @Test
    @Transactional
    public void createQuizExercise() throws Exception {
        int databaseSizeBeforeCreate = quizExerciseRepository.findAll().size();

        // Create the QuizExercise
        restQuizExerciseMockMvc.perform(post("/api/quiz-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizExercise)))
            .andExpect(status().isCreated());

        // Validate the QuizExercise in the database
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeCreate + 1);
        QuizExercise testQuizExercise = quizExerciseList.get(quizExerciseList.size() - 1);
    }

    @Test
    @Transactional
    public void createQuizExerciseWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = quizExerciseRepository.findAll().size();

        // Create the QuizExercise with an existing ID
        quizExercise.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restQuizExerciseMockMvc.perform(post("/api/quiz-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizExercise)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllQuizExercises() throws Exception {
        // Initialize the database
        quizExerciseRepository.saveAndFlush(quizExercise);

        // Get all the quizExerciseList
        restQuizExerciseMockMvc.perform(get("/api/quiz-exercises?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(quizExercise.getId().intValue())));
    }

    @Test
    @Transactional
    public void getQuizExercise() throws Exception {
        // Initialize the database
        quizExerciseRepository.saveAndFlush(quizExercise);

        // Get the quizExercise
        restQuizExerciseMockMvc.perform(get("/api/quiz-exercises/{id}", quizExercise.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(quizExercise.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingQuizExercise() throws Exception {
        // Get the quizExercise
        restQuizExerciseMockMvc.perform(get("/api/quiz-exercises/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateQuizExercise() throws Exception {
        // Initialize the database
        quizExerciseRepository.saveAndFlush(quizExercise);
        int databaseSizeBeforeUpdate = quizExerciseRepository.findAll().size();

        // Update the quizExercise
        QuizExercise updatedQuizExercise = quizExerciseRepository.findOne(quizExercise.getId());

        restQuizExerciseMockMvc.perform(put("/api/quiz-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedQuizExercise)))
            .andExpect(status().isOk());

        // Validate the QuizExercise in the database
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeUpdate);
        QuizExercise testQuizExercise = quizExerciseList.get(quizExerciseList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingQuizExercise() throws Exception {
        int databaseSizeBeforeUpdate = quizExerciseRepository.findAll().size();

        // Create the QuizExercise

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restQuizExerciseMockMvc.perform(put("/api/quiz-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(quizExercise)))
            .andExpect(status().isCreated());

        // Validate the QuizExercise in the database
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteQuizExercise() throws Exception {
        // Initialize the database
        quizExerciseRepository.saveAndFlush(quizExercise);
        int databaseSizeBeforeDelete = quizExerciseRepository.findAll().size();

        // Get the quizExercise
        restQuizExerciseMockMvc.perform(delete("/api/quiz-exercises/{id}", quizExercise.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<QuizExercise> quizExerciseList = quizExerciseRepository.findAll();
        assertThat(quizExerciseList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(QuizExercise.class);
        QuizExercise quizExercise1 = new QuizExercise();
        quizExercise1.setId(1L);
        QuizExercise quizExercise2 = new QuizExercise();
        quizExercise2.setId(quizExercise1.getId());
        assertThat(quizExercise1).isEqualTo(quizExercise2);
        quizExercise2.setId(2L);
        assertThat(quizExercise1).isNotEqualTo(quizExercise2);
        quizExercise1.setId(null);
        assertThat(quizExercise1).isNotEqualTo(quizExercise2);
    }
}
