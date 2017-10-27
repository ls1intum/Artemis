package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.DragAndDropStatistic;
import de.tum.in.www1.exerciseapp.repository.DragAndDropStatisticRepository;
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
 * Test class for the DragAndDropStatisticResource REST controller.
 *
 * @see DragAndDropStatisticResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class DragAndDropStatisticResourceIntTest {

    @Autowired
    private DragAndDropStatisticRepository dragAndDropStatisticRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restDragAndDropStatisticMockMvc;

    private DragAndDropStatistic dragAndDropStatistic;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        DragAndDropStatisticResource dragAndDropStatisticResource = new DragAndDropStatisticResource(dragAndDropStatisticRepository);
        this.restDragAndDropStatisticMockMvc = MockMvcBuilders.standaloneSetup(dragAndDropStatisticResource)
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
    public static DragAndDropStatistic createEntity(EntityManager em) {
        DragAndDropStatistic dragAndDropStatistic = new DragAndDropStatistic();
        return dragAndDropStatistic;
    }

    @Before
    public void initTest() {
        dragAndDropStatistic = createEntity(em);
    }

    @Test
    @Transactional
    public void createDragAndDropStatistic() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropStatisticRepository.findAll().size();

        // Create the DragAndDropStatistic
        restDragAndDropStatisticMockMvc.perform(post("/api/drag-and-drop-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropStatistic)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropStatistic in the database
        List<DragAndDropStatistic> dragAndDropStatisticList = dragAndDropStatisticRepository.findAll();
        assertThat(dragAndDropStatisticList).hasSize(databaseSizeBeforeCreate + 1);
        DragAndDropStatistic testDragAndDropStatistic = dragAndDropStatisticList.get(dragAndDropStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void createDragAndDropStatisticWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropStatisticRepository.findAll().size();

        // Create the DragAndDropStatistic with an existing ID
        dragAndDropStatistic.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restDragAndDropStatisticMockMvc.perform(post("/api/drag-and-drop-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropStatistic)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<DragAndDropStatistic> dragAndDropStatisticList = dragAndDropStatisticRepository.findAll();
        assertThat(dragAndDropStatisticList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllDragAndDropStatistics() throws Exception {
        // Initialize the database
        dragAndDropStatisticRepository.saveAndFlush(dragAndDropStatistic);

        // Get all the dragAndDropStatisticList
        restDragAndDropStatisticMockMvc.perform(get("/api/drag-and-drop-statistics?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dragAndDropStatistic.getId().intValue())));
    }

    @Test
    @Transactional
    public void getDragAndDropStatistic() throws Exception {
        // Initialize the database
        dragAndDropStatisticRepository.saveAndFlush(dragAndDropStatistic);

        // Get the dragAndDropStatistic
        restDragAndDropStatisticMockMvc.perform(get("/api/drag-and-drop-statistics/{id}", dragAndDropStatistic.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(dragAndDropStatistic.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingDragAndDropStatistic() throws Exception {
        // Get the dragAndDropStatistic
        restDragAndDropStatisticMockMvc.perform(get("/api/drag-and-drop-statistics/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateDragAndDropStatistic() throws Exception {
        // Initialize the database
        dragAndDropStatisticRepository.saveAndFlush(dragAndDropStatistic);
        int databaseSizeBeforeUpdate = dragAndDropStatisticRepository.findAll().size();

        // Update the dragAndDropStatistic
        DragAndDropStatistic updatedDragAndDropStatistic = dragAndDropStatisticRepository.findOne(dragAndDropStatistic.getId());

        restDragAndDropStatisticMockMvc.perform(put("/api/drag-and-drop-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedDragAndDropStatistic)))
            .andExpect(status().isOk());

        // Validate the DragAndDropStatistic in the database
        List<DragAndDropStatistic> dragAndDropStatisticList = dragAndDropStatisticRepository.findAll();
        assertThat(dragAndDropStatisticList).hasSize(databaseSizeBeforeUpdate);
        DragAndDropStatistic testDragAndDropStatistic = dragAndDropStatisticList.get(dragAndDropStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingDragAndDropStatistic() throws Exception {
        int databaseSizeBeforeUpdate = dragAndDropStatisticRepository.findAll().size();

        // Create the DragAndDropStatistic

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restDragAndDropStatisticMockMvc.perform(put("/api/drag-and-drop-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropStatistic)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropStatistic in the database
        List<DragAndDropStatistic> dragAndDropStatisticList = dragAndDropStatisticRepository.findAll();
        assertThat(dragAndDropStatisticList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteDragAndDropStatistic() throws Exception {
        // Initialize the database
        dragAndDropStatisticRepository.saveAndFlush(dragAndDropStatistic);
        int databaseSizeBeforeDelete = dragAndDropStatisticRepository.findAll().size();

        // Get the dragAndDropStatistic
        restDragAndDropStatisticMockMvc.perform(delete("/api/drag-and-drop-statistics/{id}", dragAndDropStatistic.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<DragAndDropStatistic> dragAndDropStatisticList = dragAndDropStatisticRepository.findAll();
        assertThat(dragAndDropStatisticList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DragAndDropStatistic.class);
        DragAndDropStatistic dragAndDropStatistic1 = new DragAndDropStatistic();
        dragAndDropStatistic1.setId(1L);
        DragAndDropStatistic dragAndDropStatistic2 = new DragAndDropStatistic();
        dragAndDropStatistic2.setId(dragAndDropStatistic1.getId());
        assertThat(dragAndDropStatistic1).isEqualTo(dragAndDropStatistic2);
        dragAndDropStatistic2.setId(2L);
        assertThat(dragAndDropStatistic1).isNotEqualTo(dragAndDropStatistic2);
        dragAndDropStatistic1.setId(null);
        assertThat(dragAndDropStatistic1).isNotEqualTo(dragAndDropStatistic2);
    }
}
