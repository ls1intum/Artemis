package de.tum.in.www1.artemis.domain.hestia;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.SecondaryTable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A TextHint.
 */
@Entity
@DiscriminatorValue("T")
@SecondaryTable(name = "text_hint")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextHint extends ExerciseHint {

    @Column(name = "content")
    private String content;

    public String getContent() {
        return content;
    }

    public ExerciseHint content(String content) {
        this.content = content;
        return this;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "TextHint{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", content='" + getContent() + "'" + "}";
    }
}
