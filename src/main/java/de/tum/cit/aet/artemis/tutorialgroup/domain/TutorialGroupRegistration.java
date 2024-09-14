package de.tum.cit.aet.artemis.tutorialgroup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "tutorial_group_registration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupRegistration extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "student_id")
    @NotNull
    @JsonIgnoreProperties("tutorialGroupRegistrations")
    private User student;

    @ManyToOne
    @JoinColumn(name = "tutorial_group_id")
    @NotNull
    @JsonIgnoreProperties("registrations")
    private TutorialGroup tutorialGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TutorialGroupRegistrationType type;

    public TutorialGroupRegistration() {
        // Empty constructor needed for Jackson.
    }

    public TutorialGroupRegistration(User student, TutorialGroup tutorialGroup, TutorialGroupRegistrationType type) {
        this.student = student;
        this.tutorialGroup = tutorialGroup;
        this.type = type;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public TutorialGroup getTutorialGroup() {
        return tutorialGroup;
    }

    public void setTutorialGroup(TutorialGroup tutorialGroup) {
        this.tutorialGroup = tutorialGroup;
    }

    public TutorialGroupRegistrationType getType() {
        return type;
    }

    public void setType(TutorialGroupRegistrationType type) {
        this.type = type;
    }
}
