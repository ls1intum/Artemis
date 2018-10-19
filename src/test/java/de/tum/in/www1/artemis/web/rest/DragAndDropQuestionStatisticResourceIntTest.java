package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.DragAndDropQuestionStatistic;
import de.tum.in.www1.artemis.repository.DragAndDropQuestionStatisticRepository;
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
 * Test class for the DragAndDropQuestionStatisticResource REST controller.
 *
 * @see DragAndDropQuestionStatisticResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class DragAndDropQuestionStatisticResourceIntTest {

    @Autowired
    private DragAndDropQuestionStatisticRepository dragAndDropQuestionStatisticRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restDragAndDropQuestionStatisticMockMvc;

    private DragAndDropQuestionStatistic dragAndDropQuestionStatistic;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final DragAndDropQuestionStatisticResource dragAndDropQuestionStatisticResource = new DragAndDropQuestionStatisticResource(dragAndDropQuestionStatisticRepository);
        this.restDragAndDropQuestionStatisticMockMvc = MockMvcBuilders.standaloneSetup(dragAndDropQuestionStatisticResource)
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
    public static DragAndDropQuestionStatistic createEntity(EntityManager em) {
        DragAndDropQuestionStatistic dragAndDropQuestionStatistic = new DragAndDropQuestionStatistic();
        return dragAndDropQuestionStatistic;
    }

    @Before
    public void initTest() {
        dragAndDropQuestionStatistic = createEntity(em);
    }

    @Test
    @Transactional
    public void createDragAndDropQuestionStatistic() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropQuestionStatisticRepository.findAll().size();

        // Create the DragAndDropQuestionStatistic
        restDragAndDropQuestionStatisticMockMvc.perform(post("/api/drag-and-drop-question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropQuestionStatistic)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropQuestionStatistic in the database
        List<DragAndDropQuestionStatistic> dragAndDropQuestionStatisticList = dragAndDropQuestionStatisticRepository.findAll();
        assertThat(dragAndDropQuestionStatisticList).hasSize(databaseSizeBeforeCreate + 1);
        DragAndDropQuestionStatistic testDragAndDropQuestionStatistic = dragAndDropQuestionStatisticList.get(dragAndDropQuestionStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void createDragAndDropQuestionStatisticWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropQuestionStatisticRepository.findAll().size();

        // Create the DragAndDropQuestionStatistic with an existing ID
        dragAndDropQuestionStatistic.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restDragAndDropQuestionStatisticMockMvc.perform(post("/api/drag-and-drop-question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropQuestionStatistic)))
            .andExpect(status().isBadRequest());

        // Validate the DragAndDropQuestionStatistic in the database
        List<DragAndDropQuestionStatistic> dragAndDropQuestionStatisticList = dragAndDropQuestionStatisticRepository.findAll();
        assertThat(dragAndDropQuestionStatisticList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllDragAndDropQuestionStatistics() throws Exception {
        // Initialize the database
        dragAndDropQuestionStatisticRepository.saveAndFlush(dragAndDropQuestionStatistic);

        // Get all the dragAndDropQuestionStatisticList
        restDragAndDropQuestionStatisticMockMvc.perform(get("/api/drag-and-drop-question-statistics?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dragAndDropQuestionStatistic.getId().intValue())));
    }
    
    @Test
    @Transactional
    public void getDragAndDropQuestionStatistic() throws Exception {
        // Initialize the database
        dragAndDropQuestionStatisticRepository.saveAndFlush(dragAndDropQuestionStatistic);

        // Get the dragAndDropQuestionStatistic
        restDragAndDropQuestionStatisticMockMvc.perform(get("/api/drag-and-drop-question-statistics/{id}", dragAndDropQuestionStatistic.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(dragAndDropQuestionStatistic.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingDragAndDropQuestionStatistic() throws Exception {
        // Get the dragAndDropQuestionStatistic
        restDragAndDropQuestionStatisticMockMvc.perform(get("/api/drag-and-drop-question-statistics/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateDragAndDropQuestionStatistic() throws Exception {
        // Initialize the database
        dragAndDropQuestionStatisticRepository.saveAndFlush(dragAndDropQuestionStatistic);

        int databaseSizeBeforeUpdate = dragAndDropQuestionStatisticRepository.findAll().size();

        // Update the dragAndDropQuestionStatistic
        DragAndDropQuestionStatistic updatedDragAndDropQuestionStatistic = dragAndDropQuestionStatisticRepository.findById(dragAndDropQuestionStatistic.getId()).get();
        // Disconnect from session so that the updates on updatedDragAndDropQuestionStatistic are not directly saved in db
        em.detach(updatedDragAndDropQuestionStatistic);

        restDragAndDropQuestionStatisticMockMvc.perform(put("/api/drag-and-drop-question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedDragAndDropQuestionStatistic)))
            .andExpect(status().isOk());

        // Validate the DragAndDropQuestionStatistic in the database
        List<DragAndDropQuestionStatistic> dragAndDropQuestionStatisticList = dragAndDropQuestionStatisticRepository.findAll();
        assertThat(dragAndDropQuestionStatisticList).hasSize(databaseSizeBeforeUpdate);
        DragAndDropQuestionStatistic testDragAndDropQuestionStatistic = dragAndDropQuestionStatisticList.get(dragAndDropQuestionStatisticList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingDragAndDropQuestionStatistic() throws Exception {
        int databaseSizeBeforeUpdate = dragAndDropQuestionStatisticRepository.findAll().size();

        // Create the DragAndDropQuestionStatistic

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDragAndDropQuestionStatisticMockMvc.perform(put("/api/drag-and-drop-question-statistics")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropQuestionStatistic)))
            .andExpect(status().isBadRequest());

        // Validate the DragAndDropQuestionStatistic in the database
        List<DragAndDropQuestionStatistic> dragAndDropQuestionStatisticList = dragAndDropQuestionStatisticRepository.findAll();
        assertThat(dragAndDropQuestionStatisticList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteDragAndDropQuestionStatistic() throws Exception {
        // Initialize the database
        dragAndDropQuestionStatisticRepository.saveAndFlush(dragAndDropQuestionStatistic);

        int databaseSizeBeforeDelete = dragAndDropQuestionStatisticRepository.findAll().size();

        // Get the dragAndDropQuestionStatistic
        restDragAndDropQuestionStatisticMockMvc.perform(delete("/api/drag-and-drop-question-statistics/{id}", dragAndDropQuestionStatistic.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<DragAndDropQuestionStatistic> dragAndDropQuestionStatisticList = dragAndDropQuestionStatisticRepository.findAll();
        assertThat(dragAndDropQuestionStatisticList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DragAndDropQuestionStatistic.class);
        DragAndDropQuestionStatistic dragAndDropQuestionStatistic1 = new DragAndDropQuestionStatistic();
        dragAndDropQuestionStatistic1.setId(1L);
        DragAndDropQuestionStatistic dragAndDropQuestionStatistic2 = new DragAndDropQuestionStatistic();
        dragAndDropQuestionStatistic2.setId(dragAndDropQuestionStatistic1.getId());
        assertThat(dragAndDropQuestionStatistic1).isEqualTo(dragAndDropQuestionStatistic2);
        dragAndDropQuestionStatistic2.setId(2L);
        assertThat(dragAndDropQuestionStatistic1).isNotEqualTo(dragAndDropQuestionStatistic2);
        dragAndDropQuestionStatistic1.setId(null);
        assertThat(dragAndDropQuestionStatistic1).isNotEqualTo(dragAndDropQuestionStatistic2);
    }
}
