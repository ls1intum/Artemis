package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.DragAndDropAssignment;
import de.tum.in.www1.artemis.repository.DragAndDropAssignmentRepository;
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
 * Test class for the DragAndDropAssignmentResource REST controller.
 *
 * @see DragAndDropAssignmentResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class DragAndDropAssignmentResourceIntTest {

    @Autowired
    private DragAndDropAssignmentRepository dragAndDropAssignmentRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restDragAndDropAssignmentMockMvc;

    private DragAndDropAssignment dragAndDropAssignment;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final DragAndDropAssignmentResource dragAndDropAssignmentResource = new DragAndDropAssignmentResource(dragAndDropAssignmentRepository);
        this.restDragAndDropAssignmentMockMvc = MockMvcBuilders.standaloneSetup(dragAndDropAssignmentResource)
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
    public static DragAndDropAssignment createEntity(EntityManager em) {
        DragAndDropAssignment dragAndDropAssignment = new DragAndDropAssignment();
        return dragAndDropAssignment;
    }

    @Before
    public void initTest() {
        dragAndDropAssignment = createEntity(em);
    }

    @Test
    @Transactional
    public void createDragAndDropAssignment() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropAssignmentRepository.findAll().size();

        // Create the DragAndDropAssignment
        restDragAndDropAssignmentMockMvc.perform(post("/api/drag-and-drop-assignments")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropAssignment)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropAssignment in the database
        List<DragAndDropAssignment> dragAndDropAssignmentList = dragAndDropAssignmentRepository.findAll();
        assertThat(dragAndDropAssignmentList).hasSize(databaseSizeBeforeCreate + 1);
        DragAndDropAssignment testDragAndDropAssignment = dragAndDropAssignmentList.get(dragAndDropAssignmentList.size() - 1);
    }

    @Test
    @Transactional
    public void createDragAndDropAssignmentWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropAssignmentRepository.findAll().size();

        // Create the DragAndDropAssignment with an existing ID
        dragAndDropAssignment.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restDragAndDropAssignmentMockMvc.perform(post("/api/drag-and-drop-assignments")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropAssignment)))
            .andExpect(status().isBadRequest());

        // Validate the DragAndDropAssignment in the database
        List<DragAndDropAssignment> dragAndDropAssignmentList = dragAndDropAssignmentRepository.findAll();
        assertThat(dragAndDropAssignmentList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllDragAndDropAssignments() throws Exception {
        // Initialize the database
        dragAndDropAssignmentRepository.saveAndFlush(dragAndDropAssignment);

        // Get all the dragAndDropAssignmentList
        restDragAndDropAssignmentMockMvc.perform(get("/api/drag-and-drop-assignments?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dragAndDropAssignment.getId().intValue())));
    }

    @Test
    @Transactional
    public void getDragAndDropAssignment() throws Exception {
        // Initialize the database
        dragAndDropAssignmentRepository.saveAndFlush(dragAndDropAssignment);

        // Get the dragAndDropAssignment
        restDragAndDropAssignmentMockMvc.perform(get("/api/drag-and-drop-assignments/{id}", dragAndDropAssignment.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(dragAndDropAssignment.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingDragAndDropAssignment() throws Exception {
        // Get the dragAndDropAssignment
        restDragAndDropAssignmentMockMvc.perform(get("/api/drag-and-drop-assignments/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateDragAndDropAssignment() throws Exception {
        // Initialize the database
        dragAndDropAssignmentRepository.saveAndFlush(dragAndDropAssignment);
        int databaseSizeBeforeUpdate = dragAndDropAssignmentRepository.findAll().size();

        // Update the dragAndDropAssignment
        DragAndDropAssignment updatedDragAndDropAssignment = dragAndDropAssignmentRepository.findOne(dragAndDropAssignment.getId());
        // Disconnect from session so that the updates on updatedDragAndDropAssignment are not directly saved in db
        em.detach(updatedDragAndDropAssignment);

        restDragAndDropAssignmentMockMvc.perform(put("/api/drag-and-drop-assignments")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedDragAndDropAssignment)))
            .andExpect(status().isOk());

        // Validate the DragAndDropAssignment in the database
        List<DragAndDropAssignment> dragAndDropAssignmentList = dragAndDropAssignmentRepository.findAll();
        assertThat(dragAndDropAssignmentList).hasSize(databaseSizeBeforeUpdate);
        DragAndDropAssignment testDragAndDropAssignment = dragAndDropAssignmentList.get(dragAndDropAssignmentList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingDragAndDropAssignment() throws Exception {
        int databaseSizeBeforeUpdate = dragAndDropAssignmentRepository.findAll().size();

        // Create the DragAndDropAssignment

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restDragAndDropAssignmentMockMvc.perform(put("/api/drag-and-drop-assignments")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropAssignment)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropAssignment in the database
        List<DragAndDropAssignment> dragAndDropAssignmentList = dragAndDropAssignmentRepository.findAll();
        assertThat(dragAndDropAssignmentList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteDragAndDropAssignment() throws Exception {
        // Initialize the database
        dragAndDropAssignmentRepository.saveAndFlush(dragAndDropAssignment);
        int databaseSizeBeforeDelete = dragAndDropAssignmentRepository.findAll().size();

        // Get the dragAndDropAssignment
        restDragAndDropAssignmentMockMvc.perform(delete("/api/drag-and-drop-assignments/{id}", dragAndDropAssignment.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<DragAndDropAssignment> dragAndDropAssignmentList = dragAndDropAssignmentRepository.findAll();
        assertThat(dragAndDropAssignmentList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DragAndDropAssignment.class);
        DragAndDropAssignment dragAndDropAssignment1 = new DragAndDropAssignment();
        dragAndDropAssignment1.setId(1L);
        DragAndDropAssignment dragAndDropAssignment2 = new DragAndDropAssignment();
        dragAndDropAssignment2.setId(dragAndDropAssignment1.getId());
        assertThat(dragAndDropAssignment1).isEqualTo(dragAndDropAssignment2);
        dragAndDropAssignment2.setId(2L);
        assertThat(dragAndDropAssignment1).isNotEqualTo(dragAndDropAssignment2);
        dragAndDropAssignment1.setId(null);
        assertThat(dragAndDropAssignment1).isNotEqualTo(dragAndDropAssignment2);
    }
}
