package de.tum.in.www1.artemis.domain.lecture;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("T")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextUnit extends LectureUnit {

    @Lob
    @Column(name = "content")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Removes information from a lecture unit that is not needed in the course dashboard
     */
    @Override
    public TextUnit slimDownForDashboard() {
        super.slimDownForDashboard();
        this.content = "";
        return this;
    }
}
