package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;

import de.tum.in.www1.exerciseapp.domain.DragItem;
import de.tum.in.www1.exerciseapp.repository.DragItemRepository;
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
 * Test class for the DragItemResource REST controller.
 *
 * @see DragItemResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ExerciseApplicationApp.class)
public class DragItemResourceIntTest {

    private static final String DEFAULT_PICTURE_FILE_PATH = "AAAAAAAAAA";
    private static final String UPDATED_PICTURE_FILE_PATH = "BBBBBBBBBB";

    private static final String DEFAULT_TEXT = "AAAAAAAAAA";
    private static final String UPDATED_TEXT = "BBBBBBBBBB";

    private static final Integer DEFAULT_CORRECT_SCORE = 1;
    private static final Integer UPDATED_CORRECT_SCORE = 2;

    private static final Integer DEFAULT_INCORRECT_SCORE = 1;
    private static final Integer UPDATED_INCORRECT_SCORE = 2;

    @Autowired
    private DragItemRepository dragItemRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restDragItemMockMvc;

    private DragItem dragItem;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final DragItemResource dragItemResource = new DragItemResource(dragItemRepository);
        this.restDragItemMockMvc = MockMvcBuilders.standaloneSetup(dragItemResource)
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
    public static DragItem createEntity(EntityManager em) {
        DragItem dragItem = new DragItem()
            .pictureFilePath(DEFAULT_PICTURE_FILE_PATH)
            .text(DEFAULT_TEXT)
            .correctScore(DEFAULT_CORRECT_SCORE)
            .incorrectScore(DEFAULT_INCORRECT_SCORE);
        return dragItem;
    }

    @Before
    public void initTest() {
        dragItem = createEntity(em);
    }

    @Test
    @Transactional
    public void createDragItem() throws Exception {
        int databaseSizeBeforeCreate = dragItemRepository.findAll().size();

        // Create the DragItem
        restDragItemMockMvc.perform(post("/api/drag-items")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragItem)))
            .andExpect(status().isCreated());

        // Validate the DragItem in the database
        List<DragItem> dragItemList = dragItemRepository.findAll();
        assertThat(dragItemList).hasSize(databaseSizeBeforeCreate + 1);
        DragItem testDragItem = dragItemList.get(dragItemList.size() - 1);
        assertThat(testDragItem.getPictureFilePath()).isEqualTo(DEFAULT_PICTURE_FILE_PATH);
        assertThat(testDragItem.getText()).isEqualTo(DEFAULT_TEXT);
        assertThat(testDragItem.getCorrectScore()).isEqualTo(DEFAULT_CORRECT_SCORE);
        assertThat(testDragItem.getIncorrectScore()).isEqualTo(DEFAULT_INCORRECT_SCORE);
    }

    @Test
    @Transactional
    public void createDragItemWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = dragItemRepository.findAll().size();

        // Create the DragItem with an existing ID
        dragItem.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restDragItemMockMvc.perform(post("/api/drag-items")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragItem)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<DragItem> dragItemList = dragItemRepository.findAll();
        assertThat(dragItemList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllDragItems() throws Exception {
        // Initialize the database
        dragItemRepository.saveAndFlush(dragItem);

        // Get all the dragItemList
        restDragItemMockMvc.perform(get("/api/drag-items?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dragItem.getId().intValue())))
            .andExpect(jsonPath("$.[*].pictureFilePath").value(hasItem(DEFAULT_PICTURE_FILE_PATH.toString())))
            .andExpect(jsonPath("$.[*].text").value(hasItem(DEFAULT_TEXT.toString())))
            .andExpect(jsonPath("$.[*].correctScore").value(hasItem(DEFAULT_CORRECT_SCORE)))
            .andExpect(jsonPath("$.[*].incorrectScore").value(hasItem(DEFAULT_INCORRECT_SCORE)));
    }

    @Test
    @Transactional
    public void getDragItem() throws Exception {
        // Initialize the database
        dragItemRepository.saveAndFlush(dragItem);

        // Get the dragItem
        restDragItemMockMvc.perform(get("/api/drag-items/{id}", dragItem.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(dragItem.getId().intValue()))
            .andExpect(jsonPath("$.pictureFilePath").value(DEFAULT_PICTURE_FILE_PATH.toString()))
            .andExpect(jsonPath("$.text").value(DEFAULT_TEXT.toString()))
            .andExpect(jsonPath("$.correctScore").value(DEFAULT_CORRECT_SCORE))
            .andExpect(jsonPath("$.incorrectScore").value(DEFAULT_INCORRECT_SCORE));
    }

    @Test
    @Transactional
    public void getNonExistingDragItem() throws Exception {
        // Get the dragItem
        restDragItemMockMvc.perform(get("/api/drag-items/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateDragItem() throws Exception {
        // Initialize the database
        dragItemRepository.saveAndFlush(dragItem);
        int databaseSizeBeforeUpdate = dragItemRepository.findAll().size();

        // Update the dragItem
        DragItem updatedDragItem = dragItemRepository.findOne(dragItem.getId());
        updatedDragItem
            .pictureFilePath(UPDATED_PICTURE_FILE_PATH)
            .text(UPDATED_TEXT)
            .correctScore(UPDATED_CORRECT_SCORE)
            .incorrectScore(UPDATED_INCORRECT_SCORE);

        restDragItemMockMvc.perform(put("/api/drag-items")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedDragItem)))
            .andExpect(status().isOk());

        // Validate the DragItem in the database
        List<DragItem> dragItemList = dragItemRepository.findAll();
        assertThat(dragItemList).hasSize(databaseSizeBeforeUpdate);
        DragItem testDragItem = dragItemList.get(dragItemList.size() - 1);
        assertThat(testDragItem.getPictureFilePath()).isEqualTo(UPDATED_PICTURE_FILE_PATH);
        assertThat(testDragItem.getText()).isEqualTo(UPDATED_TEXT);
        assertThat(testDragItem.getCorrectScore()).isEqualTo(UPDATED_CORRECT_SCORE);
        assertThat(testDragItem.getIncorrectScore()).isEqualTo(UPDATED_INCORRECT_SCORE);
    }

    @Test
    @Transactional
    public void updateNonExistingDragItem() throws Exception {
        int databaseSizeBeforeUpdate = dragItemRepository.findAll().size();

        // Create the DragItem

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restDragItemMockMvc.perform(put("/api/drag-items")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dragItem)))
            .andExpect(status().isCreated());

        // Validate the DragItem in the database
        List<DragItem> dragItemList = dragItemRepository.findAll();
        assertThat(dragItemList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteDragItem() throws Exception {
        // Initialize the database
        dragItemRepository.saveAndFlush(dragItem);
        int databaseSizeBeforeDelete = dragItemRepository.findAll().size();

        // Get the dragItem
        restDragItemMockMvc.perform(delete("/api/drag-items/{id}", dragItem.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<DragItem> dragItemList = dragItemRepository.findAll();
        assertThat(dragItemList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DragItem.class);
        DragItem dragItem1 = new DragItem();
        dragItem1.setId(1L);
        DragItem dragItem2 = new DragItem();
        dragItem2.setId(dragItem1.getId());
        assertThat(dragItem1).isEqualTo(dragItem2);
        dragItem2.setId(2L);
        assertThat(dragItem1).isNotEqualTo(dragItem2);
        dragItem1.setId(null);
        assertThat(dragItem1).isNotEqualTo(dragItem2);
    }
}
