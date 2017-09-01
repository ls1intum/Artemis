package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.DragAndDropSubmittedAnswer;
import de.tum.in.www1.exerciseapp.repository.DragAndDropSubmittedAnswerRepository;
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
 * Test class for the DragAndDropSubmittedAnswerResource REST controller.
 *
 * @see DragAndDropSubmittedAnswerResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class DragAndDropSubmittedAnswerResourceIntTest {

    @Autowired
    private DragAndDropSubmittedAnswerRepository dragAndDropSubmittedAnswerRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restDragAndDropSubmittedAnswerMockMvc;

    private DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final DragAndDropSubmittedAnswerResource dragAndDropSubmittedAnswerResource = new DragAndDropSubmittedAnswerResource(dragAndDropSubmittedAnswerRepository);
        this.restDragAndDropSubmittedAnswerMockMvc = MockMvcBuilders.standaloneSetup(dragAndDropSubmittedAnswerResource)
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
    public static DragAndDropSubmittedAnswer createEntity(EntityManager em) {
        DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer = new DragAndDropSubmittedAnswer();
        return dragAndDropSubmittedAnswer;
    }

    @Before
    public void initTest() {
        dragAndDropSubmittedAnswer = createEntity(em);
    }

    @Test
    @Transactional
    public void createDragAndDropSubmittedAnswer() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropSubmittedAnswerRepository.findAll().size();

        // Create the DragAndDropSubmittedAnswer
        restDragAndDropSubmittedAnswerMockMvc.perform(post("/api/drag-and-drop-submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropSubmittedAnswer)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropSubmittedAnswer in the database
        List<DragAndDropSubmittedAnswer> dragAndDropSubmittedAnswerList = dragAndDropSubmittedAnswerRepository.findAll();
        assertThat(dragAndDropSubmittedAnswerList).hasSize(databaseSizeBeforeCreate + 1);
        DragAndDropSubmittedAnswer testDragAndDropSubmittedAnswer = dragAndDropSubmittedAnswerList.get(dragAndDropSubmittedAnswerList.size() - 1);
    }

    @Test
    @Transactional
    public void createDragAndDropSubmittedAnswerWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropSubmittedAnswerRepository.findAll().size();

        // Create the DragAndDropSubmittedAnswer with an existing ID
        dragAndDropSubmittedAnswer.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restDragAndDropSubmittedAnswerMockMvc.perform(post("/api/drag-and-drop-submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropSubmittedAnswer)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<DragAndDropSubmittedAnswer> dragAndDropSubmittedAnswerList = dragAndDropSubmittedAnswerRepository.findAll();
        assertThat(dragAndDropSubmittedAnswerList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllDragAndDropSubmittedAnswers() throws Exception {
        // Initialize the database
        dragAndDropSubmittedAnswerRepository.saveAndFlush(dragAndDropSubmittedAnswer);

        // Get all the dragAndDropSubmittedAnswerList
        restDragAndDropSubmittedAnswerMockMvc.perform(get("/api/drag-and-drop-submitted-answers?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dragAndDropSubmittedAnswer.getId().intValue())));
    }

    @Test
    @Transactional
    public void getDragAndDropSubmittedAnswer() throws Exception {
        // Initialize the database
        dragAndDropSubmittedAnswerRepository.saveAndFlush(dragAndDropSubmittedAnswer);

        // Get the dragAndDropSubmittedAnswer
        restDragAndDropSubmittedAnswerMockMvc.perform(get("/api/drag-and-drop-submitted-answers/{id}", dragAndDropSubmittedAnswer.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(dragAndDropSubmittedAnswer.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingDragAndDropSubmittedAnswer() throws Exception {
        // Get the dragAndDropSubmittedAnswer
        restDragAndDropSubmittedAnswerMockMvc.perform(get("/api/drag-and-drop-submitted-answers/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateDragAndDropSubmittedAnswer() throws Exception {
        // Initialize the database
        dragAndDropSubmittedAnswerRepository.saveAndFlush(dragAndDropSubmittedAnswer);
        int databaseSizeBeforeUpdate = dragAndDropSubmittedAnswerRepository.findAll().size();

        // Update the dragAndDropSubmittedAnswer
        DragAndDropSubmittedAnswer updatedDragAndDropSubmittedAnswer = dragAndDropSubmittedAnswerRepository.findOne(dragAndDropSubmittedAnswer.getId());

        restDragAndDropSubmittedAnswerMockMvc.perform(put("/api/drag-and-drop-submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedDragAndDropSubmittedAnswer)))
            .andExpect(status().isOk());

        // Validate the DragAndDropSubmittedAnswer in the database
        List<DragAndDropSubmittedAnswer> dragAndDropSubmittedAnswerList = dragAndDropSubmittedAnswerRepository.findAll();
        assertThat(dragAndDropSubmittedAnswerList).hasSize(databaseSizeBeforeUpdate);
        DragAndDropSubmittedAnswer testDragAndDropSubmittedAnswer = dragAndDropSubmittedAnswerList.get(dragAndDropSubmittedAnswerList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingDragAndDropSubmittedAnswer() throws Exception {
        int databaseSizeBeforeUpdate = dragAndDropSubmittedAnswerRepository.findAll().size();

        // Create the DragAndDropSubmittedAnswer

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restDragAndDropSubmittedAnswerMockMvc.perform(put("/api/drag-and-drop-submitted-answers")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropSubmittedAnswer)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropSubmittedAnswer in the database
        List<DragAndDropSubmittedAnswer> dragAndDropSubmittedAnswerList = dragAndDropSubmittedAnswerRepository.findAll();
        assertThat(dragAndDropSubmittedAnswerList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteDragAndDropSubmittedAnswer() throws Exception {
        // Initialize the database
        dragAndDropSubmittedAnswerRepository.saveAndFlush(dragAndDropSubmittedAnswer);
        int databaseSizeBeforeDelete = dragAndDropSubmittedAnswerRepository.findAll().size();

        // Get the dragAndDropSubmittedAnswer
        restDragAndDropSubmittedAnswerMockMvc.perform(delete("/api/drag-and-drop-submitted-answers/{id}", dragAndDropSubmittedAnswer.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<DragAndDropSubmittedAnswer> dragAndDropSubmittedAnswerList = dragAndDropSubmittedAnswerRepository.findAll();
        assertThat(dragAndDropSubmittedAnswerList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DragAndDropSubmittedAnswer.class);
        DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer1 = new DragAndDropSubmittedAnswer();
        dragAndDropSubmittedAnswer1.setId(1L);
        DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer2 = new DragAndDropSubmittedAnswer();
        dragAndDropSubmittedAnswer2.setId(dragAndDropSubmittedAnswer1.getId());
        assertThat(dragAndDropSubmittedAnswer1).isEqualTo(dragAndDropSubmittedAnswer2);
        dragAndDropSubmittedAnswer2.setId(2L);
        assertThat(dragAndDropSubmittedAnswer1).isNotEqualTo(dragAndDropSubmittedAnswer2);
        dragAndDropSubmittedAnswer1.setId(null);
        assertThat(dragAndDropSubmittedAnswer1).isNotEqualTo(dragAndDropSubmittedAnswer2);
    }
}
