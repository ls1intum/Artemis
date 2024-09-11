package de.tum.cit.aet.artemis.domain.quiz;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.config.Constants;
import de.tum.cit.aet.artemis.domain.quiz.scoring.ScoringStrategy;
import de.tum.cit.aet.artemis.domain.quiz.scoring.ScoringStrategyDragAndDropAllOrNothing;
import de.tum.cit.aet.artemis.domain.quiz.scoring.ScoringStrategyDragAndDropProportionalWithPenalty;
import de.tum.cit.aet.artemis.domain.quiz.scoring.ScoringStrategyDragAndDropProportionalWithoutPenalty;
import de.tum.cit.aet.artemis.domain.view.QuizView;
import de.tum.cit.aet.artemis.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;

/**
 * A DragAndDropQuestion.
 */
@Entity
@DiscriminatorValue(value = "DD")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropQuestion extends QuizQuestion {

    private static final Logger log = LoggerFactory.getLogger(DragAndDropQuestion.class);

    @Transient
    private final transient FileService fileService = new FileService();

    @Column(name = "background_file_path")
    @JsonView(QuizView.Before.class)
    private String backgroundFilePath;

    // TODO: making this a bidirectional relation leads to weird Hibernate behavior with missing data when loading quiz questions, we should investigate this again in the future
    // after 6.x upgrade
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    @OrderColumn
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<DropLocation> dropLocations = new ArrayList<>();

    // TODO: making this a bidirectional relation leads to weird Hibernate behavior with missing data when loading quiz questions, we should investigate this again in the future
    // after 6.x upgrade
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    @OrderColumn
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<DragItem> dragItems = new ArrayList<>();

    // TODO: making this a bidirectional relation leads to weird Hibernate behavior with missing data when loading quiz questions, we should investigate this again in the future
    // after 6.x upgrade
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    @OrderColumn
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.After.class)
    private List<DragAndDropMapping> correctMappings = new ArrayList<>();

    public String getBackgroundFilePath() {
        return backgroundFilePath;
    }

    public void setBackgroundFilePath(String backgroundFilePath) {
        this.backgroundFilePath = backgroundFilePath;
    }

    public List<DropLocation> getDropLocations() {
        return dropLocations;
    }

    public DragAndDropQuestion addDropLocation(DropLocation dropLocation) {
        this.dropLocations.add(dropLocation);
        dropLocation.setQuestion(this);
        return this;
    }

    public DragAndDropQuestion removeDropLocation(DropLocation dropLocation) {
        this.dropLocations.remove(dropLocation);
        dropLocation.setQuestion(null);
        return this;
    }

    public void setDropLocations(List<DropLocation> dropLocations) {
        this.dropLocations = dropLocations;
    }

    public List<DragItem> getDragItems() {
        return dragItems;
    }

    public DragAndDropQuestion addDragItem(DragItem dragItem) {
        this.dragItems.add(dragItem);
        dragItem.setQuestion(this);
        return this;
    }

    public DragAndDropQuestion removeDragItem(DragItem dragItem) {
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

    public DragAndDropQuestion addCorrectMapping(DragAndDropMapping dragAndDropMapping) {
        this.correctMappings.add(dragAndDropMapping);
        dragAndDropMapping.setQuestion(this);
        return this;
    }

    public DragAndDropQuestion removeCorrectMapping(DragAndDropMapping dragAndDropMapping) {
        this.correctMappings.remove(dragAndDropMapping);
        dragAndDropMapping.setQuestion(null);
        return this;
    }

    public void setCorrectMappings(List<DragAndDropMapping> dragAndDropMappings) {
        this.correctMappings = dragAndDropMappings;
    }

    @Override
    public Boolean isValid() {
        // check general validity (using superclass)
        if (!super.isValid()) {
            return false;
        }

        // A drag item can either be a text or a picture, but not both or none
        for (DragItem dragItem : dragItems) {
            if (StringUtils.isEmpty(dragItem.getText()) == StringUtils.isEmpty(dragItem.getPictureFilePath())) {
                return false;
            }
        }

        // check if at least one correct mapping exists
        return getCorrectMappings() != null && !getCorrectMappings().isEmpty();

        // TODO: (?) Add checks for "is solvable" and "no misleading correct mapping" --> look at the implementation in the client
    }

    /**
     * This method is called after the entity is saved for the first time. We replace the placeholder in the backgroundFilePath with the id of the entity because we don't know it
     * before creation.
     */
    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (backgroundFilePath != null && backgroundFilePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            backgroundFilePath = backgroundFilePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
    }

    /**
     * This method is called when deleting the entity. It makes sure that the corresponding file is deleted as well.
     */
    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        try {
            if (backgroundFilePath != null) {
                fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(URI.create(backgroundFilePath)), 0);
            }
        }
        catch (FilePathParsingException e) {
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
    @Override
    public void undoUnallowedChanges(QuizQuestion originalQuizQuestion) {
        if (originalQuizQuestion instanceof DragAndDropQuestion dndOriginalQuestion) {
            backgroundFilePath = dndOriginalQuestion.getBackgroundFilePath();
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
    @Override
    public boolean isUpdateOfResultsAndStatisticsNecessary(QuizQuestion originalQuizQuestion) {
        if (originalQuizQuestion instanceof DragAndDropQuestion dndOriginalQuestion) {
            return checkDragItemsIfRecalculationIsNecessary(dndOriginalQuestion) || checkDropLocationsIfRecalculationIsNecessary(dndOriginalQuestion)
                    || !getCorrectMappings().equals(dndOriginalQuestion.getCorrectMappings());
        }
        return false;
    }

    @Override
    @JsonIgnore
    public void initializeStatistic() {
        setQuizQuestionStatistic(new DragAndDropQuestionStatistic());
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
