package de.tum.in.www1.artemis.domain.lecture;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("T")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextUnit extends LectureUnit {

    // @Lob
    @Column(name = "content")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
