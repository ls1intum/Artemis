package de.tum.cit.aet.artemis.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;

/**
 * A Team of students.
 */
@Entity
@Table(name = "team", uniqueConstraints = { @UniqueConstraint(columnNames = { "exercise_id", "short_name" }) })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Team extends AbstractAuditingEntity implements Participant {

    @Column(name = "name")
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "image")
    private String image;

    @ManyToOne
    @JsonIgnore
    private Exercise exercise;

    /**
     * The cache concurrency strategy needs to be READ_WRITE (and not NONSTRICT_READ_WRITE) since the non-strict mode will cause an SQLIntegrityConstraintViolationException to
     * occur when trying to persist the entity for the first time after changes have been made to the related entities of the ManyToMany relationship (users in this case).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JoinTable(name = "team_student", joinColumns = @JoinColumn(name = "team_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "student_id", referencedColumnName = "id"))
    private Set<User> students = new HashSet<>();

    @ManyToOne
    private User owner;

    public Team() {
    }

    /**
     * Copy constructor (generates a copy of team with no exercise assigned yet)
     *
     * @param team Team which to copy
     */
    public Team(@NotNull Team team) {
        this.name = team.name;
        this.shortName = team.shortName;
        this.image = team.image;
        this.students.addAll(team.students);
        this.owner = team.owner;
    }

    public Team id(Long id) {
        this.setId(id);
        return this;
    }

    @Override
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

    public String getShortName() {
        return shortName;
    }

    public Team shortName(String shortName) {
        this.shortName = shortName;
        return this;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    @Override
    public String getParticipantIdentifier() {
        return shortName;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public Team exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Set<User> getStudents() {
        return students;
    }

    public boolean hasStudent(User user) {
        return students.contains(user);
    }

    public boolean hasStudentWithLogin(String login) {
        return students.stream().anyMatch(student -> student.getLogin().equals(login));
    }

    public Team students(Set<User> users) {
        this.students = users;
        return this;
    }

    public Team addStudents(User user) {
        this.students.add(user);
        return this;
    }

    public Team removeStudents(User user) {
        this.students.remove(user);
        return this;
    }

    public void setStudents(Set<User> users) {
        this.students = users;
    }

    public User getOwner() {
        return owner;
    }

    public boolean isOwner(User user) {
        return this.owner != null && this.owner.equals(user);
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    @JsonIgnore(false)
    @JsonProperty
    @Override
    public String getCreatedBy() {
        return super.getCreatedBy();
    }

    @JsonIgnore(false)
    @JsonProperty
    @Override
    public Instant getCreatedDate() {
        return super.getCreatedDate();
    }

    @JsonIgnore(false)
    @JsonProperty
    @Override
    public String getLastModifiedBy() {
        return super.getLastModifiedBy();
    }

    @JsonIgnore(false)
    @JsonProperty
    @Override
    public Instant getLastModifiedDate() {
        return super.getLastModifiedDate();
    }

    public void filterSensitiveInformation() {
        this.students.forEach(student -> {
            student.setLangKey(null);
            student.setLastNotificationRead(null);
        });
    }

    @Override
    @JsonIgnore
    public Set<User> getParticipants() {
        return getStudents();
    }

    @Override
    public String toString() {
        return "Team{" + "id=" + getId() + ", name='" + getName() + "'" + ", shortName='" + getShortName() + "'" + ", image='" + getImage() + "'" + "}";
    }
}
