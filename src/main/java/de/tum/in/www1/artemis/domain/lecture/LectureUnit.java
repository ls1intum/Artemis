package de.tum.in.www1.artemis.domain.lecture;

import java.time.ZonedDateTime;
import java.util.*;

import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import org.hibernate.annotations.*;
import org.hibernate.annotations.Cache;

import com.fasterxml.jackson.annotation.*;
import de.tum.in.www1.artemis.domain.*;

@Entity
@Table(name = "lecture_unit")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("L")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Annotation necessary to distinguish between concrete implementations of lecture-content when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = AttachmentUnit.class, name = "attachment"), @JsonSubTypes.Type(value = ExerciseUnit.class, name = "exercise"),
        @JsonSubTypes.Type(value = TextUnit.class, name = "text"), @JsonSubTypes.Type(value = VideoUnit.class, name = "video"), })
public abstract class LectureUnit extends DomainObject {

    @Transient
    private boolean visibleToStudents;

    @Column(name = "name")
    private String name;

    @Column(name = "release_date")
    private ZonedDateTime releaseDate;

    @ManyToOne
    @JoinColumn(name = "lecture_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Lecture lecture;

    @ManyToMany(mappedBy = "lectureUnits")
    @OrderBy("title")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    public Set<LearningGoal> learningGoals = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Set<LearningGoal> getLearningGoals() {
        return learningGoals;
    }

    public void setLearningGoals(Set<LearningGoal> learningGoals) {
        this.learningGoals = learningGoals;
    }

    @JsonProperty("visibleToStudents")
    public boolean isVisibleToStudents() {
        if (releaseDate == null) {
            return true;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

}
