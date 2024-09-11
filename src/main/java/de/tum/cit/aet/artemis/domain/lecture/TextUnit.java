package de.tum.cit.aet.artemis.domain.lecture;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("T")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextUnit extends LectureUnit {

    @Column(name = "content")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // IMPORTANT NOTICE: The following string has to be consistent with the one defined in LectureUnit.java
    @Override
    public String getType() {
        return "text";
    }
}
