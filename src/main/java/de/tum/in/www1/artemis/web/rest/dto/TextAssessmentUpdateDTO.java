package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import de.tum.in.www1.artemis.domain.AssessmentUpdate;
import de.tum.in.www1.artemis.domain.TextBlock;

public class TextAssessmentUpdateDTO extends AssessmentUpdate {

    private List<TextBlock> textBlocks;

    public List<TextBlock> getTextBlocks() {
        return textBlocks;
    }

    public void setTextBlocks(List<TextBlock> textBlocks) {
        this.textBlocks = textBlocks;
    }
}
