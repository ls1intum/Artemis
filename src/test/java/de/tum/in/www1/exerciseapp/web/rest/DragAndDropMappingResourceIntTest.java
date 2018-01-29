package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.DragAndDropMapping;
import de.tum.in.www1.exerciseapp.repository.DragAndDropMappingRepository;
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

import static de.tum.in.www1.exerciseapp.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the DragAndDropMappingResource REST controller.
 *
 * @see DragAndDropMappingResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class DragAndDropMappingResourceIntTest {

    private static final Integer DEFAULT_DRAG_ITEM_INDEX = 1;
    private static final Integer UPDATED_DRAG_ITEM_INDEX = 2;

    private static final Integer DEFAULT_DROP_LOCATION_INDEX = 1;
    private static final Integer UPDATED_DROP_LOCATION_INDEX = 2;

    private static final Boolean DEFAULT_INVALID = false;
    private static final Boolean UPDATED_INVALID = true;

    @Autowired
    private DragAndDropMappingRepository dragAndDropMappingRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restDragAndDropMappingMockMvc;

    private DragAndDropMapping dragAndDropMapping;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final DragAndDropMappingResource dragAndDropMappingResource = new DragAndDropMappingResource(dragAndDropMappingRepository);
        this.restDragAndDropMappingMockMvc = MockMvcBuilders.standaloneSetup(dragAndDropMappingResource)
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
    public static DragAndDropMapping createEntity(EntityManager em) {
        DragAndDropMapping dragAndDropMapping = new DragAndDropMapping()
            .dragItemIndex(DEFAULT_DRAG_ITEM_INDEX)
            .dropLocationIndex(DEFAULT_DROP_LOCATION_INDEX)
            .invalid(DEFAULT_INVALID);
        return dragAndDropMapping;
    }

    @Before
    public void initTest() {
        dragAndDropMapping = createEntity(em);
    }

    @Test
    @Transactional
    public void createDragAndDropMapping() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropMappingRepository.findAll().size();

        // Create the DragAndDropMapping
        restDragAndDropMappingMockMvc.perform(post("/api/drag-and-drop-mappings")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropMapping)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropMapping in the database
        List<DragAndDropMapping> dragAndDropMappingList = dragAndDropMappingRepository.findAll();
        assertThat(dragAndDropMappingList).hasSize(databaseSizeBeforeCreate + 1);
        DragAndDropMapping testDragAndDropMapping = dragAndDropMappingList.get(dragAndDropMappingList.size() - 1);
        assertThat(testDragAndDropMapping.getDragItemIndex()).isEqualTo(DEFAULT_DRAG_ITEM_INDEX);
        assertThat(testDragAndDropMapping.getDropLocationIndex()).isEqualTo(DEFAULT_DROP_LOCATION_INDEX);
        assertThat(testDragAndDropMapping.isInvalid()).isEqualTo(DEFAULT_INVALID);
    }

    @Test
    @Transactional
    public void createDragAndDropMappingWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = dragAndDropMappingRepository.findAll().size();

        // Create the DragAndDropMapping with an existing ID
        dragAndDropMapping.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restDragAndDropMappingMockMvc.perform(post("/api/drag-and-drop-mappings")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropMapping)))
            .andExpect(status().isBadRequest());

        // Validate the DragAndDropMapping in the database
        List<DragAndDropMapping> dragAndDropMappingList = dragAndDropMappingRepository.findAll();
        assertThat(dragAndDropMappingList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllDragAndDropMappings() throws Exception {
        // Initialize the database
        dragAndDropMappingRepository.saveAndFlush(dragAndDropMapping);

        // Get all the dragAndDropMappingList
        restDragAndDropMappingMockMvc.perform(get("/api/drag-and-drop-mappings?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dragAndDropMapping.getId().intValue())))
            .andExpect(jsonPath("$.[*].dragItemIndex").value(hasItem(DEFAULT_DRAG_ITEM_INDEX)))
            .andExpect(jsonPath("$.[*].dropLocationIndex").value(hasItem(DEFAULT_DROP_LOCATION_INDEX)))
            .andExpect(jsonPath("$.[*].invalid").value(hasItem(DEFAULT_INVALID.booleanValue())));
    }

    @Test
    @Transactional
    public void getDragAndDropMapping() throws Exception {
        // Initialize the database
        dragAndDropMappingRepository.saveAndFlush(dragAndDropMapping);

        // Get the dragAndDropMapping
        restDragAndDropMappingMockMvc.perform(get("/api/drag-and-drop-mappings/{id}", dragAndDropMapping.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(dragAndDropMapping.getId().intValue()))
            .andExpect(jsonPath("$.dragItemIndex").value(DEFAULT_DRAG_ITEM_INDEX))
            .andExpect(jsonPath("$.dropLocationIndex").value(DEFAULT_DROP_LOCATION_INDEX))
            .andExpect(jsonPath("$.invalid").value(DEFAULT_INVALID.booleanValue()));
    }

    @Test
    @Transactional
    public void getNonExistingDragAndDropMapping() throws Exception {
        // Get the dragAndDropMapping
        restDragAndDropMappingMockMvc.perform(get("/api/drag-and-drop-mappings/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateDragAndDropMapping() throws Exception {
        // Initialize the database
        dragAndDropMappingRepository.saveAndFlush(dragAndDropMapping);
        int databaseSizeBeforeUpdate = dragAndDropMappingRepository.findAll().size();

        // Update the dragAndDropMapping
        DragAndDropMapping updatedDragAndDropMapping = dragAndDropMappingRepository.findOne(dragAndDropMapping.getId());
        updatedDragAndDropMapping
            .dragItemIndex(UPDATED_DRAG_ITEM_INDEX)
            .dropLocationIndex(UPDATED_DROP_LOCATION_INDEX)
            .invalid(UPDATED_INVALID);

        restDragAndDropMappingMockMvc.perform(put("/api/drag-and-drop-mappings")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedDragAndDropMapping)))
            .andExpect(status().isOk());

        // Validate the DragAndDropMapping in the database
        List<DragAndDropMapping> dragAndDropMappingList = dragAndDropMappingRepository.findAll();
        assertThat(dragAndDropMappingList).hasSize(databaseSizeBeforeUpdate);
        DragAndDropMapping testDragAndDropMapping = dragAndDropMappingList.get(dragAndDropMappingList.size() - 1);
        assertThat(testDragAndDropMapping.getDragItemIndex()).isEqualTo(UPDATED_DRAG_ITEM_INDEX);
        assertThat(testDragAndDropMapping.getDropLocationIndex()).isEqualTo(UPDATED_DROP_LOCATION_INDEX);
        assertThat(testDragAndDropMapping.isInvalid()).isEqualTo(UPDATED_INVALID);
    }

    @Test
    @Transactional
    public void updateNonExistingDragAndDropMapping() throws Exception {
        int databaseSizeBeforeUpdate = dragAndDropMappingRepository.findAll().size();

        // Create the DragAndDropMapping

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restDragAndDropMappingMockMvc.perform(put("/api/drag-and-drop-mappings")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragAndDropMapping)))
            .andExpect(status().isCreated());

        // Validate the DragAndDropMapping in the database
        List<DragAndDropMapping> dragAndDropMappingList = dragAndDropMappingRepository.findAll();
        assertThat(dragAndDropMappingList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteDragAndDropMapping() throws Exception {
        // Initialize the database
        dragAndDropMappingRepository.saveAndFlush(dragAndDropMapping);
        int databaseSizeBeforeDelete = dragAndDropMappingRepository.findAll().size();

        // Get the dragAndDropMapping
        restDragAndDropMappingMockMvc.perform(delete("/api/drag-and-drop-mappings/{id}", dragAndDropMapping.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<DragAndDropMapping> dragAndDropMappingList = dragAndDropMappingRepository.findAll();
        assertThat(dragAndDropMappingList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DragAndDropMapping.class);
        DragAndDropMapping dragAndDropMapping1 = new DragAndDropMapping();
        dragAndDropMapping1.setId(1L);
        DragAndDropMapping dragAndDropMapping2 = new DragAndDropMapping();
        dragAndDropMapping2.setId(dragAndDropMapping1.getId());
        assertThat(dragAndDropMapping1).isEqualTo(dragAndDropMapping2);
        dragAndDropMapping2.setId(2L);
        assertThat(dragAndDropMapping1).isNotEqualTo(dragAndDropMapping2);
        dragAndDropMapping1.setId(null);
        assertThat(dragAndDropMapping1).isNotEqualTo(dragAndDropMapping2);
    }
}
