package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

public class LectureUnitSplitDTO {

    public String unitName;

    public ZonedDateTime releaseDate;

    public String startPage;

    public String endPage;

    public LectureUnitSplitDTO() {
    }

    public LectureUnitSplitDTO(String unitName, ZonedDateTime releaseDate, String startPage, String endPage) {
        this.unitName = unitName;
        this.releaseDate = releaseDate;
        this.startPage = startPage;
        this.endPage = endPage;
    }

    public void setStartPage(String startPage) {
        this.startPage = startPage;
    }

    public void setEndPage(String endPage) {
        this.endPage = endPage;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

}
