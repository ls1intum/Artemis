package de.tum.in.www1.artemis.domain.lecture_unit;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("T")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
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
}
