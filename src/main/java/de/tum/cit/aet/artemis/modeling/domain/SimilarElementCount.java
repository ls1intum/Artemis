package de.tum.cit.aet.artemis.modeling.domain;

import de.tum.cit.aet.artemis.modeling.repository.ModelElementRepository;

public class SimilarElementCount implements ModelElementRepository.ModelElementCount {

    private String elementId;

    private Long numberOfOtherElements;

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    @Override
    public String getElementId() {
        return elementId;
    }

    public void setNumberOfOtherElements(Long numberOfOtherElements) {
        this.numberOfOtherElements = numberOfOtherElements;
    }

    @Override
    public Long getNumberOfOtherElements() {
        return numberOfOtherElements;
    }
}
