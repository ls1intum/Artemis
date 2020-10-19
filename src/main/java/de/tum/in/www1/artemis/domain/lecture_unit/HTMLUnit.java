package de.tum.in.www1.artemis.domain.lecture_unit;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

@Entity
@DiscriminatorValue("H")
public class HTMLUnit extends LectureUnit {

    @Lob
    @Column(name = "markdown")
    private String markdown;

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }
}
