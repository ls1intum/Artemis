package de.tum.in.www1.artemis.domain.modeling;

import de.tum.in.www1.artemis.repository.ModelElementRepository;

public class SimilarElementCount implements ModelElementRepository.ModelElementCount {

    private String elementId;

    private Long numberOfOtherElements;

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public String getElementId() {
        return elementId;
    }

    public void setNumberOfOtherElements(Long numberOfOtherElements) {
        this.numberOfOtherElements = numberOfOtherElements;
    }

    public Long getNumberOfOtherElements() {
        return numberOfOtherElements;
    }
}
