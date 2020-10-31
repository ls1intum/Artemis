package de.tum.in.www1.artemis.domain.lecture_unit;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("V")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class VideoUnit extends LectureUnit {

    @Column(name = "description")
    @Lob
    private String description;

    @Column(name = "source")
    @Lob
    private String source;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
