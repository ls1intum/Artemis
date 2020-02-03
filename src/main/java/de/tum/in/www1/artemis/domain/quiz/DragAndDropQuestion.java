package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.*;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A DragAndDropQuestion.
 */
@Entity
@DiscriminatorValue(value = "DD")
@JsonTypeName("drag-and-drop")
public class DragAndDropQuestion extends QuizQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Transient
    private FileService fileService = new FileService();

    @Transient
    private String prevBackgroundFilePath;

    @Column(name = "background_file_path")
    @JsonView(QuizView.Before.class)
    private String backgroundFilePath;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<DropLocation> dropLocations = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<DragItem> dragItems = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.After.class)
    private List<DragAndDropMapping> correctMappings = new ArrayList<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public String getBackgroundFilePath() {
        return backgroundFilePath;
    }

    public DragAndDropQuestion backgroundFilePath(String backgroundFilePath) {
        this.backgroundFilePath = backgroundFilePath;
        return this;
    }

    public void setBackgroundFilePath(String backgroundFilePath) {
        this.backgroundFilePath = backgroundFilePath;
    }

    public List<DropLocation> getDropLocations() {
        return dropLocations;
    }

    public DragAndDropQuestion dropLocations(List<DropLocation> dropLocations) {
        this.dropLocations = dropLocations;
        return this;
    }

    public void setDropLocations(List<DropLocation> dropLocations) {
        this.dropLocations = dropLocations;
    }

    public List<DragItem> getDragItems() {
        return dragItems;
    }

    public DragAndDropQuestion dragItems(List<DragItem> dragItems) {
        this.dragItems = dragItems;
        return this;
    }

    public DragAndDropQuestion addDragItems(DragItem dragItem) {
        this.dragItems.add(dragItem);
        dragItem.setQuestion(this);
        return this;
    }

    public DragAndDropQuestion removeDragItems(DragItem dragItem) {
        this.dragItems.remove(dragItem);
        dragItem.setQuestion(null);
        return this;
    }

    public void setDragItems(List<DragItem> dragItems) {
        this.dragItems = dragItems;
    }

    public List<DragAndDropMapping> getCorrectMappings() {
        return correctMappings;
    }

    public DragAndDropQuestion correctMappings(List<DragAndDropMapping> dragAndDropMappings) {
        this.correctMappings = dragAndDropMappings;
        return this;
    }

    public DragAndDropQuestion addCorrectMappings(DragAndDropMapping dragAndDropMapping) {
        this.correctMappings.add(dragAndDropMapping);
        dragAndDropMapping.setQuestion(this);
        return this;
    }

    public DragAndDropQuestion removeCorrectMappings(DragAndDropMapping dragAndDropMapping) {
        this.correctMappings.remove(dragAndDropMapping);
        dragAndDropMapping.setQuestion(null);
        return this;
    }

    public void setCorrectMappings(List<DragAndDropMapping> dragAndDropMappings) {
        this.correctMappings = dragAndDropMappings;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public Boolean isValid() {
        // check general validity (using superclass)
        if (!super.isValid()) {
            return false;
        }

        // check if at least one correct mapping exists
        return getCorrectMappings() != null && !getCorrectMappings().isEmpty();

        // TODO (?): Add checks for "is solvable" and "no misleading correct mapping" --> look at the implementation in the client
    }

    /*
     * NOTE: The file management is necessary to differentiate between temporary and used files and to delete used files when the corresponding question is deleted or it is
     * replaced by another file. The workflow is as follows 1. user uploads a file -> this is a temporary file, because at this point the corresponding question might not exist
     * yet. 2. user saves the question -> now we move the temporary file which is addressed in backgroundFilePath to a permanent location and update the value in backgroundFilePath
     * accordingly. => This happens in @PrePersist and @PostPersist 3. user might upload another file to replace the existing file -> this new file is a temporary file at first 4.
     * user saves changes (with the new backgroundFilePath pointing to the new temporary file) -> now we delete the old file in the permanent location and move the new file to a
     * permanent location and update the value in backgroundFilePath accordingly. => This happens in @PreUpdate and uses @PostLoad to know the old path 5. When question is deleted,
     * the file in the permanent location is deleted => This happens in @PostRemove
     */

    /**
     *Initialisation of the DragAndDropQuestion on Server start
     */
    @PostLoad
    public void onLoad() {
        // replace placeholder with actual id if necessary (this is needed because changes made in afterCreate() are not persisted)
        if (backgroundFilePath != null && backgroundFilePath.contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            backgroundFilePath = backgroundFilePath.replace(Constants.FILEPATH_ID_PLACHEOLDER, getId().toString());
        }
        // save current path as old path (needed to know old path in onUpdate() and onDelete())
        prevBackgroundFilePath = backgroundFilePath;
    }

    @PrePersist
    public void beforeCreate() {
        // move file if necessary (id at this point will be null, so placeholder will be inserted)
        backgroundFilePath = fileService.manageFilesForUpdatedFilePath(prevBackgroundFilePath, backgroundFilePath, Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH, getId());
    }

    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (backgroundFilePath != null && backgroundFilePath.contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            backgroundFilePath = backgroundFilePath.replace(Constants.FILEPATH_ID_PLACHEOLDER, getId().toString());
        }
    }

    @PreUpdate
    public void onUpdate() {
        // move file and delete old file if necessary
        backgroundFilePath = fileService.manageFilesForUpdatedFilePath(prevBackgroundFilePath, backgroundFilePath, Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH, getId());
    }

    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        fileService.manageFilesForUpdatedFilePath(prevBackgroundFilePath, null, Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH, getId());
    }

    /**
     * Get all drag items that are mapped to the given drop location
     *
     * @param dropLocation the drop location we want to find the correct drag items for
     * @return all drag items that are defined as correct for this drop location
     */
    public Set<DragItem> getCorrectDragItemsForDropLocation(DropLocation dropLocation) {
        Set<DragItem> result = new HashSet<>();
        for (DragAndDropMapping mapping : correctMappings) {
            if (mapping.getDropLocation().equals(dropLocation)) {
                result.add(mapping.getDragItem());
            }
        }
        return result;
    }

    /**
     * Get dragItem by ID
     *
     * @param dragItemId the ID of the dragItem, which should be found
     * @return the dragItem with the given ID, or null if the dragItem is not contained in this question
     */
    public DragItem findDragItemById(Long dragItemId) {

        if (dragItemId != null) {
            // iterate through all dragItems of this quiz
            for (DragItem dragItem : dragItems) {
                // return dragItem if the IDs are equal
                if (dragItem.getId().equals(dragItemId)) {
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
            // iterate through all dropLocations of this quiz
            for (DropLocation dropLocation : dropLocations) {
                // return dropLocation if the IDs are equal
                if (dropLocation.getId().equals(dropLocationId)) {
                    return dropLocation;
                }
            }
        }
        return null;
    }

    /**
     * undo all dragItem- and dropLocation-changes which are not allowed ( adding them)
     *
     * @param originalQuizQuestion the original QuizQuestion-object, which will be compared with this question
     */
    public void undoUnallowedChanges(QuizQuestion originalQuizQuestion) {

        if (originalQuizQuestion instanceof DragAndDropQuestion) {
            DragAndDropQuestion dndOriginalQuestion = (DragAndDropQuestion) originalQuizQuestion;
            // undo unallowed dragItemChanges
            undoUnallowedDragItemChanges(dndOriginalQuestion);
            // undo unallowed dragItemChanges
            undoUnallowedDropLocationChanges(dndOriginalQuestion);
        }
    }

    /**
     * undo all dragItem-changes which are not allowed ( adding them)
     *
     * @param originalQuestion the original DragAndDrop-object, which will be compared with this question
     */
    private void undoUnallowedDragItemChanges(DragAndDropQuestion originalQuestion) {

        // find added DragItems, which are not allowed to be added
        Set<DragItem> notAllowedAddedDragItems = new HashSet<>();
        // check every dragItem of the question
        for (DragItem dragItem : this.getDragItems()) {
            // check if the dragItem were already in the originalQuestion -> if not it's an added dragItem
            if (originalQuestion.getDragItems().contains(dragItem)) {
                // find original dragItem
                DragItem originalDragItem = originalQuestion.findDragItemById(dragItem.getId());
                // correct invalid = null to invalid = false
                if (dragItem.isInvalid() == null) {
                    dragItem.setInvalid(false);
                }
                // reset invalid dragItem if it already set to true (it's not possible to set a dragItem valid again)
                dragItem.setInvalid(dragItem.isInvalid() || (originalDragItem.isInvalid() != null && originalDragItem.isInvalid()));
            }
            else {
                // mark the added dragItem (adding dragItems is not allowed)
                notAllowedAddedDragItems.add(dragItem);
            }
        }
        // remove the added dragItems
        this.getDragItems().removeAll(notAllowedAddedDragItems);
    }

    /**
     * undo all dropLocation-changes which are not allowed ( adding them)
     *
     * @param originalQuestion the original DragAndDrop-object, which will be compared with this question
     */
    private void undoUnallowedDropLocationChanges(DragAndDropQuestion originalQuestion) {

        // find added DropLocations, which are not allowed to be added
        Set<DropLocation> notAllowedAddedDropLocations = new HashSet<>();
        // check every dropLocation of the question
        for (DropLocation dropLocation : this.getDropLocations()) {
            // check if the dropLocation were already in the originalQuestion -> if not it's an added dropLocation
            if (originalQuestion.getDropLocations().contains(dropLocation)) {
                // find original dropLocation
                DropLocation originalDropLocation = originalQuestion.findDropLocationById(dropLocation.getId());
                // correct invalid = null to invalid = false
                if (dropLocation.isInvalid() == null) {
                    dropLocation.setInvalid(false);
                }
                // reset invalid dropLocation if it already set to true (it's not possible to set a dropLocation valid again)
                dropLocation.setInvalid(dropLocation.isInvalid() || (originalDropLocation.isInvalid() != null && originalDropLocation.isInvalid()));
            }
            else {
                // mark the added dropLocation (adding dropLocations is not allowed)
                notAllowedAddedDropLocations.add(dropLocation);
            }
        }
        // remove the added dropLocations
        this.getDropLocations().removeAll(notAllowedAddedDropLocations);
    }

    /**
     * check if an update of the Results and Statistics is necessary
     *
     * @param originalQuizQuestion the original QuizQuestion-object, which will be compared with this question
     * @return a boolean which is true if the dragItem and dropLocation-changes make an update necessary and false if not
     */
    public boolean isUpdateOfResultsAndStatisticsNecessary(QuizQuestion originalQuizQuestion) {
        if (originalQuizQuestion instanceof DragAndDropQuestion) {
            DragAndDropQuestion dndOriginalQuestion = (DragAndDropQuestion) originalQuizQuestion;
            return checkDragItemsIfRecalculationIsNecessary(dndOriginalQuestion) || checkDropLocationsIfRecalculationIsNecessary(dndOriginalQuestion)
                    || !getCorrectMappings().equals(dndOriginalQuestion.getCorrectMappings());
        }
        return false;
    }

    /**
     * check dragItems if an update of the Results and Statistics is necessary
     *
     * @param originalQuestion the original DragAndDropQuestion-object, which will be compared with this question
     * @return a boolean which is true if the dragItem-changes make an update necessary and false if not
     */
    private boolean checkDragItemsIfRecalculationIsNecessary(DragAndDropQuestion originalQuestion) {

        boolean updateNecessary = false;

        // check every dragItem of the question
        for (DragItem dragItem : this.getDragItems()) {
            // check if the dragItem were already in the originalQuizExercise
            if (originalQuestion.getDragItems().contains(dragItem)) {
                // find original dragItem
                DragItem originalDragItem = originalQuestion.findDragItemById(dragItem.getId());

                // check if a dragItem is set invalid
                // if true an update of the Statistics and Results is necessary
                if ((dragItem.isInvalid() && !this.isInvalid() && originalDragItem.isInvalid() == null)
                        || (dragItem.isInvalid() && !this.isInvalid() && !originalDragItem.isInvalid())) {
                    updateNecessary = true;
                }
            }
        }
        // check if a dragItem was deleted (not allowed added dragItems are not relevant)
        // if true an update of the Statistics and Results is necessary
        if (this.getDragItems().size() < originalQuestion.getDragItems().size()) {
            updateNecessary = true;
        }
        return updateNecessary;
    }

    /**
     * check DropLocations if an update of the Results and Statistics is necessary
     *
     * @param originalQuestion the original DragAndDropQuestion-object, which will be compared with this question
     * @return a boolean which is true if the dropLocation-changes make an update necessary and false if not
     */
    private boolean checkDropLocationsIfRecalculationIsNecessary(DragAndDropQuestion originalQuestion) {

        boolean updateNecessary = false;

        // check every dropLocation of the question
        for (DropLocation dropLocation : this.getDropLocations()) {
            // check if the dropLocation were already in the originalQuizExercise
            if (originalQuestion.getDropLocations().contains(dropLocation)) {
                // find original dropLocation
                DropLocation originalDropLocation = originalQuestion.findDropLocationById(dropLocation.getId());

                // check if a dropLocation is set invalid
                // if true an update of the Statistics and Results is necessary
                if ((dropLocation.isInvalid() && !this.isInvalid() && originalDropLocation.isInvalid() == null)
                        || (dropLocation.isInvalid() && !this.isInvalid() && !originalDropLocation.isInvalid())) {
                    updateNecessary = true;
                }
            }
        }
        // check if a dropLocation was deleted (not allowed added dropLocations are not relevant)
        // if true an update of the Statistics and Results is necessary
        if (this.getDropLocations().size() < originalQuestion.getDropLocations().size()) {
            updateNecessary = true;
        }
        return updateNecessary;
    }

    @Override
    public void filterForStudentsDuringQuiz() {
        super.filterForStudentsDuringQuiz();
        setCorrectMappings(null);
    }

    @Override
    public void filterForStatisticWebsocket() {
        super.filterForStatisticWebsocket();
        setCorrectMappings(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) o;
        if (dragAndDropQuestion.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragAndDropQuestion.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
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
