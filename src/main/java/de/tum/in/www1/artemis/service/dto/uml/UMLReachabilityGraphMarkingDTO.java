package de.tum.in.www1.artemis.service.dto.uml;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude
public class UMLReachabilityGraphMarkingDTO extends UMLElementDTO {

    private boolean isInitialMarking;

    public UMLReachabilityGraphMarkingDTO(String id, String name, String type, Bounds bounds, boolean isInitialMarking) {
        super(id, name, type, bounds);
        this.isInitialMarking = isInitialMarking;
    }

    public boolean getIsInitialMarking() {
        return isInitialMarking;
    }

    public void setIsInitialMarking(boolean initialMarking) {
        isInitialMarking = initialMarking;
    }
}
