package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "quiz_group")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizGroup extends DomainObject {

    @Column(name = "name", nullable = false)
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
