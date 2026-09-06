package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostRemove;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.ServedFileUrl;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategy;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyDragAndDropAllOrNothing;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyDragAndDropProportionalWithPenalty;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyDragAndDropProportionalWithoutPenalty;

/**
 * A DragAndDropQuestion.
 * <p>
 * Its drop locations, drag items and correct mappings are no longer separate JPA entity tables: they are stored inside the {@code quiz_question.content} JSON column as a
 * {@link DragAndDropQuestionContent}. This eliminates the eager {@code @OneToMany} fan-out that produced a Cartesian-product blow-up when loading quizzes with many drop locations.
 * The public accessors below ({@code getDropLocations()} / {@code getDragItems()} / {@code getCorrectMappings()}) keep their original signatures and delegate to the content, so
 * the
 * REST/websocket wire format and all callers are preserved. Correct mappings are stored normalized (id-based, see {@link DragAndDropCorrectMapping}) and resolved to object-based
 * {@link DragAndDropMapping}s on access.
 */
@Entity
@DiscriminatorValue(value = "DD")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropQuestion extends QuizQuestion {

    private static final Logger log = LoggerFactory.getLogger(DragAndDropQuestion.class);

    @Transient
    private final transient FileService fileService = new FileService();

    @Column(name = "background_file_path")
    private String backgroundFilePath;

    /**
     * @return the drag-and-drop content, creating and attaching an empty one if none exists yet.
     */
    private DragAndDropQuestionContent dndContent() {
        if (getContent() instanceof DragAndDropQuestionContent dragAndDropContent) {
            return dragAndDropContent;
        }
        DragAndDropQuestionContent created = new DragAndDropQuestionContent();
        setContent(created);
        return created;
    }

    /**
     * Mint a fresh, question-scoped component id: one greater than the largest id currently used by any drop location, drag item or correct mapping of this question.
     *
     * @return the next free component id
     */
    private long nextComponentId() {
        long max = 0;
        for (Long id : dndContent().componentIds()) {
            if (id != null && id > max) {
                max = id;
            }
        }
        return max + 1;
    }

    /**
     * The filename of the background image, which is the whole of what is stored.
     * <p>
     * The quiz create and update requests reuse this field to name a file that is being uploaded alongside the exercise, so the service layer has to see the value as it stands
     * and not a URL built around it. The URL the client is served is {@link #servedBackgroundFilePath()}.
     *
     * @return the stored filename of the background image
     */
    @JsonIgnore
    public String getBackgroundFilePath() {
        return backgroundFilePath;
    }

    /**
     * The path the background image is served under, relative to {@code api/core/files/}.
     * <p>
     * This is the value the client receives under the name {@code backgroundFilePath}. It cannot be built before the question has been inserted, because the URL is scoped by the
     * question id; until then the stored filename is handed out, which is also what the client sends while it is uploading a new image.
     *
     * @return the served path of the background image
     */
    @JsonProperty("backgroundFilePath")
    public String servedBackgroundFilePath() {
        return ServedFileUrl.dragAndDropBackground(getId(), backgroundFilePath);
    }

    /**
     * Stores the filename of the given value. See {@link FileSystemLocation#storedFilename} for why a served URL sent back by a client cannot end up in the column.
     *
     * @param backgroundFilePath the filename of the background image, or the URL it is served under
     */
    @JsonProperty("backgroundFilePath")
    public void setBackgroundFilePath(String backgroundFilePath) {
        this.backgroundFilePath = FileSystemLocation.storedFilename(backgroundFilePath);
    }

    public List<DropLocation> getDropLocations() {
        return dndContent().getDropLocations();
    }

    public void setDropLocations(List<DropLocation> dropLocations) {
        dndContent().setDropLocations(dropLocations);
        assignMissingComponentIds(dndContent().getDropLocations());
    }

    /**
     * Assign a fresh, question-scoped id to every component in the given list that does not have one yet. Called from the entity-level bulk setters used by the create/edit/import
     * flows; the JSON deserialization path goes through {@link DragAndDropQuestionContent}'s own setters instead and therefore preserves existing ids.
     *
     * @param components the components to assign ids to
     */
    private void assignMissingComponentIds(List<? extends DomainObject> components) {
        for (var component : components) {
            if (component.getId() == null) {
                component.setId(nextComponentId());
            }
        }
    }

    /**
     * Mint a fresh, question-scoped id for any drop location or drag item added without one (e.g. via {@code getDropLocations().add(...)} / {@code getDragItems().add(...)}, which
     * bypass {@link #addDropLocation} / {@link #addDragItem}). Called before persisting so the statistics counters (keyed by drop-location id) and the stored JSON content stay
     * id-consistent.
     */
    public void assignMissingComponentIds() {
        assignMissingComponentIds(getDropLocations());
        assignMissingComponentIds(getDragItems());
    }

    /**
     * Adds a single drop location, assigning it a fresh question-scoped id if it does not have one yet.
     *
     * @param dropLocation the drop location to add
     * @return this question for fluent chaining
     */
    public DragAndDropQuestion addDropLocation(DropLocation dropLocation) {
        if (dropLocation.getId() == null) {
            dropLocation.setId(nextComponentId());
        }
        dndContent().getDropLocations().add(dropLocation);
        return this;
    }

    /**
     * Removes a single drop location.
     *
     * @param dropLocation the drop location to remove
     * @return this question for fluent chaining
     */
    public DragAndDropQuestion removeDropLocation(DropLocation dropLocation) {
        dndContent().getDropLocations().remove(dropLocation);
        return this;
    }

    public List<DragItem> getDragItems() {
        return dndContent().getDragItems();
    }

    public void setDragItems(List<DragItem> dragItems) {
        dndContent().setDragItems(dragItems);
        assignMissingComponentIds(dndContent().getDragItems());
    }

    /**
     * Adds a single drag item, assigning it a fresh question-scoped id if it does not have one yet.
     *
     * @param dragItem the drag item to add
     * @return this question for fluent chaining
     */
    public DragAndDropQuestion addDragItem(DragItem dragItem) {
        if (dragItem.getId() == null) {
            dragItem.setId(nextComponentId());
        }
        dndContent().getDragItems().add(dragItem);
        return this;
    }

    /**
     * Removes a single drag item.
     *
     * @param dragItem the drag item to remove
     * @return this question for fluent chaining
     */
    public DragAndDropQuestion removeDragItem(DragItem dragItem) {
        dndContent().getDragItems().remove(dragItem);
        return this;
    }

    /**
     * The correct mappings resolved into object-based {@link DragAndDropMapping}s (with their drag item / drop location objects and derived indices). Built on demand from the
     * normalized id-based mappings stored in {@link DragAndDropQuestionContent}. Mutating the returned set does not affect the stored mappings — use {@link #addCorrectMapping},
     * {@link #removeCorrectMapping} or {@link #setCorrectMappings} instead.
     *
     * @return the resolved correct mappings
     */
    public Set<DragAndDropMapping> getCorrectMappings() {
        Set<DragAndDropMapping> result = new HashSet<>();
        List<DropLocation> dropLocations = dndContent().getDropLocations();
        List<DragItem> dragItems = dndContent().getDragItems();
        for (DragAndDropCorrectMapping entry : dndContent().getCorrectMappings()) {
            DragItem dragItem = findDragItemById(entry.getDragItemId());
            DropLocation dropLocation = findDropLocationById(entry.getDropLocationId());
            // skip stale mappings whose drag item or drop location no longer exists (e.g. removed during re-evaluation)
            if (dragItem == null || dropLocation == null) {
                continue;
            }
            DragAndDropMapping mapping = new DragAndDropMapping();
            mapping.setId(entry.getId());
            mapping.setInvalid(entry.isInvalid());
            mapping.setDragItem(dragItem);
            mapping.setDropLocation(dropLocation);
            if (dragItem != null) {
                int index = dragItems.indexOf(dragItem);
                mapping.setDragItemIndex(index >= 0 ? index : null);
            }
            if (dropLocation != null) {
                int index = dropLocations.indexOf(dropLocation);
                mapping.setDropLocationIndex(index >= 0 ? index : null);
            }
            result.add(mapping);
        }
        return result;
    }

    /**
     * Replaces the correct mappings, storing them id-based in the content. Missing mapping ids are minted question-scoped.
     *
     * @param correctMappings the object-based correct mappings to store
     */
    public void setCorrectMappings(Set<DragAndDropMapping> correctMappings) {
        List<DragAndDropCorrectMapping> entries = new ArrayList<>();
        if (correctMappings != null) {
            for (DragAndDropMapping mapping : correctMappings) {
                entries.add(toEntry(mapping));
            }
        }
        dndContent().setCorrectMappings(entries);
        // Mint ids only after the entries are attached to the content, so nextComponentId() sees each freshly-assigned id and does not hand out the same id to multiple mappings.
        for (DragAndDropCorrectMapping entry : dndContent().getCorrectMappings()) {
            if (entry.getId() == null) {
                entry.setId(nextComponentId());
            }
        }
    }

    /**
     * Adds a single correct mapping, assigning it a fresh question-scoped id if it does not have one yet. The referenced drag item and drop location must already have ids (i.e. be
     * added to this question first).
     *
     * @param mapping the correct mapping to add
     * @return this question for fluent chaining
     */
    public DragAndDropQuestion addCorrectMapping(DragAndDropMapping mapping) {
        Long dragItemId = mapping.getDragItem() != null ? mapping.getDragItem().getId() : null;
        Long dropLocationId = mapping.getDropLocation() != null ? mapping.getDropLocation().getId() : null;
        // Skip a duplicate mapping for the same (drag item, drop location) pair: the former Set<DragAndDropMapping> storage deduplicated by id, so preserve that behavior against a
        // request that sends the same pair twice.
        boolean alreadyMapped = dndContent().getCorrectMappings().stream()
                .anyMatch(entry -> Objects.equals(entry.getDragItemId(), dragItemId) && Objects.equals(entry.getDropLocationId(), dropLocationId));
        if (alreadyMapped) {
            return this;
        }
        if (mapping.getId() == null) {
            mapping.setId(nextComponentId());
        }
        dndContent().getCorrectMappings().add(toEntry(mapping));
        return this;
    }

    /**
     * Removes the correct mapping with the same drag item and drop location as the given mapping.
     *
     * @param mapping the correct mapping to remove
     * @return this question for fluent chaining
     */
    public DragAndDropQuestion removeCorrectMapping(DragAndDropMapping mapping) {
        Long dragItemId = mapping.getDragItem() != null ? mapping.getDragItem().getId() : null;
        Long dropLocationId = mapping.getDropLocation() != null ? mapping.getDropLocation().getId() : null;
        dndContent().getCorrectMappings().removeIf(entry -> Objects.equals(entry.getDragItemId(), dragItemId) && Objects.equals(entry.getDropLocationId(), dropLocationId));
        return this;
    }

    /**
     * Removes correct-mapping entries whose drag item or drop location no longer exists on this question (e.g. after a component was deleted during re-evaluation). Keeps the
     * stored
     * content free of orphan mappings so {@link #isValid()} / {@code nextComponentId()} stay accurate and the JSON does not grow across repeated re-evaluations.
     */
    public void removeOrphanCorrectMappings() {
        dndContent().getCorrectMappings().removeIf(entry -> findDragItemById(entry.getDragItemId()) == null || findDropLocationById(entry.getDropLocationId()) == null);
    }

    private DragAndDropCorrectMapping toEntry(DragAndDropMapping mapping) {
        Long dragItemId = mapping.getDragItem() != null ? mapping.getDragItem().getId() : null;
        Long dropLocationId = mapping.getDropLocation() != null ? mapping.getDropLocation().getId() : null;
        // id may be null here; setCorrectMappings mints missing ids after the entries are attached to the content (addCorrectMapping mints before calling this).
        return new DragAndDropCorrectMapping(mapping.getId(), dragItemId, dropLocationId, mapping.isInvalid());
    }

    @Override
    public Boolean isValid() {
        // check general validity (using superclass)
        if (!super.isValid()) {
            return false;
        }

        // A drag item can either be a text or a picture, but not both or none
        for (DragItem dragItem : getDragItems()) {
            if (StringUtils.isEmpty(dragItem.getText()) == StringUtils.isEmpty(dragItem.getPictureFilePath())) {
                return false;
            }
        }

        // check if at least one correct mapping exists (resolved getter, so orphan mappings left behind by a re-evaluation deletion don't count)
        return !getCorrectMappings().isEmpty();
    }

    /**
     * This method is called when deleting the entity. It makes sure that the corresponding background file is deleted as well. Drag item picture files are deleted explicitly by
     * the
     * service layer (see {@code QuizExerciseService}) since drag items are no longer JPA entities with a {@code @PostRemove} callback.
     */
    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        try {
            if (backgroundFilePath != null) {
                fileService.schedulePathForDeletion(new FileSystemLocation.DragAndDropBackground(backgroundFilePath).path(), 0);
            }
        }
        catch (IllegalArgumentException e) {
            // if the file path is invalid, we don't need to delete it
            log.warn("Could not delete file with path {}. Assume already deleted, DragAndDropQuestion {} can be removed.", backgroundFilePath, getId());
        }
    }

    /**
     * Get all drag items that are mapped to the given drop location
     *
     * @param dropLocation the drop location we want to find the correct drag items for
     * @return all drag items that are defined as correct for this drop location
     */
    public Set<DragItem> getCorrectDragItemsForDropLocation(DropLocation dropLocation) {
        Set<DragItem> result = new HashSet<>();
        if (dropLocation == null || dropLocation.getId() == null) {
            return result;
        }
        for (DragAndDropCorrectMapping mapping : dndContent().getCorrectMappings()) {
            if (dropLocation.getId().equals(mapping.getDropLocationId())) {
                DragItem dragItem = findDragItemById(mapping.getDragItemId());
                if (dragItem != null) {
                    result.add(dragItem);
                }
            }
        }
        return result;
    }

    /**
     * Check whether the given drop location was solved correctly in the given submitted answer. This used to live on {@link DropLocation}, but now that a drop location no longer
     * has
     * a back-reference to its question, the owning question resolves the mappings.
     *
     * @param dropLocation the drop location to check
     * @param dndAnswer    the student's submitted answer
     * @return true if the drop location is correct
     */
    public boolean isDropLocationCorrect(DropLocation dropLocation, DragAndDropSubmittedAnswer dndAnswer) {
        Set<DragItem> correctDragItems = getCorrectDragItemsForDropLocation(dropLocation);
        DragItem selectedDragItem = dndAnswer.getSelectedDragItemForDropLocation(dropLocation);

        // the drop location was meant to stay empty and the user didn't drag anything onto it
        // OR the user dragged one of the correct drag items onto this drop location
        return (correctDragItems.isEmpty() && selectedDragItem == null) || (selectedDragItem != null && correctDragItems.contains(selectedDragItem));
    }

    /**
     * Get dragItem by ID
     *
     * @param dragItemId the ID of the dragItem, which should be found
     * @return the dragItem with the given ID, or null if the dragItem is not contained in this question
     */
    public DragItem findDragItemById(Long dragItemId) {
        if (dragItemId != null) {
            for (DragItem dragItem : dndContent().getDragItems()) {
                if (dragItemId.equals(dragItem.getId())) {
                    return dragItem;
                }
            }
        }
        return null;
    }

    /**
     * Get dropLocation by ID
     *
     * @param dropLocationId the ID of the dropLocation, which should be found
     * @return the dropLocation with the given ID, or null if the dropLocation is not contained in this question
     */
    public DropLocation findDropLocationById(Long dropLocationId) {
        if (dropLocationId != null) {
            for (DropLocation dropLocation : dndContent().getDropLocations()) {
                if (dropLocationId.equals(dropLocation.getId())) {
                    return dropLocation;
                }
            }
        }
        return null;
    }

    @Override
    @JsonIgnore
    public void initializeStatistic() {
        setQuizQuestionStatistic(new DragAndDropQuestionStatistic());
    }

    @Override
    public void filterForStudentsDuringQuiz() {
        super.filterForStudentsDuringQuiz();
        dndContent().setCorrectMappings(new ArrayList<>());
    }

    @Override
    public void filterForStatisticWebsocket() {
        super.filterForStatisticWebsocket();
        dndContent().setCorrectMappings(new ArrayList<>());
    }

    /**
     * creates an instance of ScoringStrategy with the appropriate type for the given drag and drop question (based on polymorphism)
     *
     * @return an instance of the appropriate implementation of ScoringStrategy
     */
    @Override
    public ScoringStrategy makeScoringStrategy() {
        return switch (getScoringType()) {
            case ALL_OR_NOTHING -> new ScoringStrategyDragAndDropAllOrNothing();
            case PROPORTIONAL_WITH_PENALTY -> new ScoringStrategyDragAndDropProportionalWithPenalty();
            case PROPORTIONAL_WITHOUT_PENALTY -> new ScoringStrategyDragAndDropProportionalWithoutPenalty();
        };
    }

    @Override
    public String toString() {
        return "DragAndDropQuestion{" + "id=" + getId() + ", backgroundFilePath='" + getBackgroundFilePath() + "'" + "}";
    }

    @Override
    public QuizQuestion copyQuestionId() {
        var question = new DragAndDropQuestion();
        question.setId(getId());
        return question;
    }
}
