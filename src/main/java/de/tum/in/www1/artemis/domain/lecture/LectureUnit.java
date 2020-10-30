package de.tum.in.www1.artemis.domain.lecture_unit;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.Lecture;

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

    public void addLearningGoal(LearningGoal learningGoal) {
        this.learningGoals.add(learningGoal);
        learningGoal.getLectureUnits().add(this);
    }

    public void removeLearningGoal(LearningGoal learningGoal) {
        this.learningGoals.remove(learningGoal);
        learningGoal.getLectureUnits().remove(this);
    }

    @JsonProperty("visibleToStudents")
    public boolean isVisibleToStudents() {
        if (releaseDate == null) {
            return true;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

}
