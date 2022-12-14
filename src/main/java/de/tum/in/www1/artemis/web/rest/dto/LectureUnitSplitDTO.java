package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

public class LectureUnitSplitDTO {

    public String attachmentName;

    public String description;

    public byte[] file;

    public ZonedDateTime releaseDate;

    public String version;

    public String startPage;

    public String endPage;

    public LectureUnitSplitDTO() {
    }

    public LectureUnitSplitDTO(String attachmentName, String description, byte[] file, ZonedDateTime releaseDate, String version, String startPage, String endPage) {
        this.attachmentName = attachmentName;
        this.description = description;
        this.file = file;
        this.releaseDate = releaseDate;
        this.version = version;
        this.startPage = startPage;
        this.endPage = endPage;
    }

    public String getStartPage() {
        return startPage;
    }

    public void setStartPage(String startPage) {
        this.startPage = startPage;
    }

    public String getEndPage() {
        return endPage;
    }

    public void setEndPage(String endPage) {
        this.endPage = endPage;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public byte[] getFile() {
        return file;
    }

    public void setFile(byte[] file) {
        this.file = file;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
