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
}
