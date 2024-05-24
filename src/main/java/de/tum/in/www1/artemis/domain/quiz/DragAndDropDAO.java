package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DragAndDropDAO implements Serializable {

    private List<DropLocation> dropLocations = new ArrayList<>();

    private List<DragItem> dragItems = new ArrayList<>();

    private List<DragAndDropMapping> correctMappings = new ArrayList<>();

    public List<DropLocation> getDropLocations() {
        return dropLocations;
    }

    public void setDropLocations(List<DropLocation> dropLocations) {
        this.dropLocations = dropLocations;
    }

    public List<DragItem> getDragItems() {
        return dragItems;
    }

    public void setDragItems(List<DragItem> dragItems) {
        this.dragItems = dragItems;
    }

    public List<DragAndDropMapping> getCorrectMappings() {
        return correctMappings;
    }

    public void setCorrectMappings(List<DragAndDropMapping> dragAndDropMappings) {
        this.correctMappings = dragAndDropMappings;
    }

    public DragAndDropDAO addDropLocation(DropLocation dropLocation) {
        this.dropLocations.add(dropLocation);
        return this;
    }

    public DragAndDropDAO removeDropLocation(DropLocation dropLocation) {
        this.dropLocations.remove(dropLocation);
        return this;
    }

    public DragAndDropDAO addDragItem(DragItem dragItem) {
        this.dragItems.add(dragItem);
        return this;
    }

    public DragAndDropDAO removeDragItem(DragItem dragItem) {
        this.dragItems.remove(dragItem);
        return this;
    }

    public DragAndDropDAO addCorrectMapping(DragAndDropMapping dragAndDropMapping) {
        this.correctMappings.add(dragAndDropMapping);
        return this;
    }

    public DragAndDropDAO removeCorrectMapping(DragAndDropMapping dragAndDropMapping) {
        this.correctMappings.remove(dragAndDropMapping);
        return this;
    }
}
