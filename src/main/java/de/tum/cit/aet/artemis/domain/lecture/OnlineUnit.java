package de.tum.cit.aet.artemis.domain.lecture;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("O")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OnlineUnit extends LectureUnit {

    @Column(name = "description")
    private String description;

    @Column(name = "source")
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

    // IMPORTANT NOTICE: The following string has to be consistent with the one defined in LectureUnit.java
    @Override
    public String getType() {
        return "online";
    }
}
