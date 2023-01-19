package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

public class LectureUnitInformationDTO {

    public List<LectureUnitSplitDTO> units;

    public int numberOfPages;

    public boolean removeBreakSlides;

    public LectureUnitInformationDTO(List<LectureUnitSplitDTO> units, int numberOfPages, boolean removeBreakSlides) {
        this.units = units;
        this.numberOfPages = numberOfPages;
        this.removeBreakSlides = removeBreakSlides;
    }
}
