package de.tum.in.www1.artemis.domain.team;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.in.www1.artemis.domain.User;

/**
 * A Team of students.
 */
@Entity
@Table(name = "team")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "T")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
// Annotation necessary to distinguish between concrete implementations of Team when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = ExerciseTeam.class, name = "exercise"), @JsonSubTypes.Type(value = CourseTeam.class, name = "course"), })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Team implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "image")
    private String image;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    protected Set<TeamStudent> students = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Team name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public Team image(String image) {
        this.image = image;
        return this;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Set<TeamStudent> getStudents() {
        return students;
    }

    public Team students(Set<TeamStudent> teamStudents) {
        this.students = teamStudents;
        return this;
    }

    public void setStudents(Set<TeamStudent> teamStudents) {
        this.students = teamStudents;
    }

    public abstract Team addStudents(User user);

    public Team removeStudents(User user) {
        for (Iterator<TeamStudent> iterator = this.students.iterator(); iterator.hasNext();) {
            TeamStudent teamStudent = iterator.next();
            if (teamStudent.getTeam().equals(this) && teamStudent.getStudent().equals(user)) {
                iterator.remove();
                teamStudent.setTeam(null);
                teamStudent.setStudent(null);
            }
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Team team = (Team) o;
        if (team.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), team.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Team{" + "id=" + getId() + ", name='" + getName() + "'" + ", image='" + getImage() + "'" + "}";
    }
}
