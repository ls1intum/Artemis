package de.tum.in.www1.artemis.web.rest.dto;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextAssessmentUpdateDTO extends AssessmentUpdate {

    private Set<TextBlock> textBlocks = new HashSet<>();

    public Set<TextBlock> getTextBlocks() {
        return textBlocks;
    }

    public void setTextBlocks(Set<TextBlock> textBlocks) {
        this.textBlocks = textBlocks;
    }
}
