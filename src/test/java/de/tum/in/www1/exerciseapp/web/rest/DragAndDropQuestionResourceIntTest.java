package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.DragAndDropQuestion;
import de.tum.in.www1.exerciseapp.repository.DragAndDropQuestionRepository;
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
 * Test class for the DragAndDropQuestionResource REST controller.
 *
 * @see DragAndDropQuestionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class DragAndDropQuestionResourceIntTest {

    private static final String DEFAULT_BACKGROUND_FILE_PATH = "AAAAAAAAAA";
    private static final String UPDATED_BACKGROUND_FILE_PATH = "BBBBBBBBBB";

    @Autowired
    private DragAndDropQuestionRepository dragAndDropQuestionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restDragAndDropQuestionMockMvc;

    private DragAndDropQuestion dragAndDropQuestion;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final DragAndDropQuestionResource dragAndDropQuestionResource = new DragAndDropQuestionResource(dragAndDropQuestionRepository);
        this.restDragAndDropQuestionMockMvc = MockMvcBuilders.standaloneSetup(dragAndDropQuestionResource)
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
    public static DragAndDropQuestion createEntity(EntityManager em) {
        DragAndDropQuestion dragAndDropQuestion = new DragAndDropQuestion()
            .backgroundFilePath(DEFAULT_BACKGROUND_FILE_PATH);
        return dragAndDropQuestion;
    }

    @Before
    public void initTest() {
        dragAndDropQuestion = createEntity(em);
    }

    @Test
    @Transactional
    public void createDragAndDropQuestion() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropQuestionRepository.findAll().size();

        // Create the DragAndDropQuestion
        restDragAndDropQuestionMockMvc.perform(post("/api/drag-and-drop-questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropQuestion)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropQuestion in the database
        List<DragAndDropQuestion> dragAndDropQuestionList = dragAndDropQuestionRepository.findAll();
        assertThat(dragAndDropQuestionList).hasSize(databaseSizeBeforeCreate + 1);
        DragAndDropQuestion testDragAndDropQuestion = dragAndDropQuestionList.get(dragAndDropQuestionList.size() - 1);
        assertThat(testDragAndDropQuestion.getBackgroundFilePath()).isEqualTo(DEFAULT_BACKGROUND_FILE_PATH);
    }

    @Test
    @Transactional
    public void createDragAndDropQuestionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropQuestionRepository.findAll().size();

        // Create the DragAndDropQuestion with an existing ID
        dragAndDropQuestion.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restDragAndDropQuestionMockMvc.perform(post("/api/drag-and-drop-questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropQuestion)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<DragAndDropQuestion> dragAndDropQuestionList = dragAndDropQuestionRepository.findAll();
        assertThat(dragAndDropQuestionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllDragAndDropQuestions() throws Exception {
        // Initialize the database
        dragAndDropQuestionRepository.saveAndFlush(dragAndDropQuestion);

        // Get all the dragAndDropQuestionList
        restDragAndDropQuestionMockMvc.perform(get("/api/drag-and-drop-questions?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dragAndDropQuestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].backgroundFilePath").value(hasItem(DEFAULT_BACKGROUND_FILE_PATH.toString())));
    }

    @Test
    @Transactional
    public void getDragAndDropQuestion() throws Exception {
        // Initialize the database
        dragAndDropQuestionRepository.saveAndFlush(dragAndDropQuestion);

        // Get the dragAndDropQuestion
        restDragAndDropQuestionMockMvc.perform(get("/api/drag-and-drop-questions/{id}", dragAndDropQuestion.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(dragAndDropQuestion.getId().intValue()))
            .andExpect(jsonPath("$.backgroundFilePath").value(DEFAULT_BACKGROUND_FILE_PATH.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingDragAndDropQuestion() throws Exception {
        // Get the dragAndDropQuestion
        restDragAndDropQuestionMockMvc.perform(get("/api/drag-and-drop-questions/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateDragAndDropQuestion() throws Exception {
        // Initialize the database
        dragAndDropQuestionRepository.saveAndFlush(dragAndDropQuestion);
        int databaseSizeBeforeUpdate = dragAndDropQuestionRepository.findAll().size();

        // Update the dragAndDropQuestion
        DragAndDropQuestion updatedDragAndDropQuestion = dragAndDropQuestionRepository.findOne(dragAndDropQuestion.getId());
        updatedDragAndDropQuestion
            .backgroundFilePath(UPDATED_BACKGROUND_FILE_PATH);

        restDragAndDropQuestionMockMvc.perform(put("/api/drag-and-drop-questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedDragAndDropQuestion)))
            .andExpect(status().isOk());

        // Validate the DragAndDropQuestion in the database
        List<DragAndDropQuestion> dragAndDropQuestionList = dragAndDropQuestionRepository.findAll();
        assertThat(dragAndDropQuestionList).hasSize(databaseSizeBeforeUpdate);
        DragAndDropQuestion testDragAndDropQuestion = dragAndDropQuestionList.get(dragAndDropQuestionList.size() - 1);
        assertThat(testDragAndDropQuestion.getBackgroundFilePath()).isEqualTo(UPDATED_BACKGROUND_FILE_PATH);
    }

    @Test
    @Transactional
    public void updateNonExistingDragAndDropQuestion() throws Exception {
        int databaseSizeBeforeUpdate = dragAndDropQuestionRepository.findAll().size();

        // Create the DragAndDropQuestion

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restDragAndDropQuestionMockMvc.perform(put("/api/drag-and-drop-questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropQuestion)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropQuestion in the database
        List<DragAndDropQuestion> dragAndDropQuestionList = dragAndDropQuestionRepository.findAll();
        assertThat(dragAndDropQuestionList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteDragAndDropQuestion() throws Exception {
        // Initialize the database
        dragAndDropQuestionRepository.saveAndFlush(dragAndDropQuestion);
        int databaseSizeBeforeDelete = dragAndDropQuestionRepository.findAll().size();

        // Get the dragAndDropQuestion
        restDragAndDropQuestionMockMvc.perform(delete("/api/drag-and-drop-questions/{id}", dragAndDropQuestion.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<DragAndDropQuestion> dragAndDropQuestionList = dragAndDropQuestionRepository.findAll();
        assertThat(dragAndDropQuestionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DragAndDropQuestion.class);
        DragAndDropQuestion dragAndDropQuestion1 = new DragAndDropQuestion();
        dragAndDropQuestion1.setId(1L);
        DragAndDropQuestion dragAndDropQuestion2 = new DragAndDropQuestion();
        dragAndDropQuestion2.setId(dragAndDropQuestion1.getId());
        assertThat(dragAndDropQuestion1).isEqualTo(dragAndDropQuestion2);
        dragAndDropQuestion2.setId(2L);
        assertThat(dragAndDropQuestion1).isNotEqualTo(dragAndDropQuestion2);
        dragAndDropQuestion1.setId(null);
        assertThat(dragAndDropQuestion1).isNotEqualTo(dragAndDropQuestion2);
    }
}
