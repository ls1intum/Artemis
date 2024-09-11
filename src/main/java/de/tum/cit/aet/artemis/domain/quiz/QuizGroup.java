package de.tum.cit.aet.artemis.domain.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.DomainObject;

@Entity
@Table(name = "quiz_group")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizGroup extends DomainObject {

    @Column(name = "name")
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonIgnore
    public boolean isValid() {
        return getName() != null && !getName().isEmpty();
    }
}
