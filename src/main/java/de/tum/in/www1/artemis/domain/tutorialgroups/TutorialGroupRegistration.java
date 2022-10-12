package de.tum.in.www1.artemis.domain.tutorialgroups;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;

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
