package de.tum.in.www1.artemis.domain.lecture_module;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("H")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class HTMLModule extends LectureModule {

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
