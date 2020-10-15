package de.tum.in.www1.artemis.domain.lecture_module;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.Lecture;

@Entity
@Table(name = "lecture_module")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("L")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of lecture-content when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = AttachmentModule.class, name = "attachment"), @JsonSubTypes.Type(value = ExerciseModule.class, name = "exercise"),
        @JsonSubTypes.Type(value = HTMLModule.class, name = "html"), @JsonSubTypes.Type(value = VideoModule.class, name = "video"), })
public abstract class LectureModule extends DomainObject {

    @Column(name = "name")
    private String name;

    @Column(name = "release_date")
    private ZonedDateTime releaseDate;

    @ManyToOne
    @JoinColumn(name = "lecture_id")
    @JsonIgnoreProperties("lectureModules")
    private Lecture lecture;

    @ManyToMany(mappedBy = "lectureModules")
    @OrderColumn(name = "learning_goal_order")
    @JsonIgnoreProperties("lectureModules")
    public List<LearningGoal> learningGoals = new ArrayList<>();

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

    public List<LearningGoal> getLearningGoals() {
        return learningGoals;
    }

    public void setLearningGoals(List<LearningGoal> learningGoals) {
        this.learningGoals = learningGoals;
    }

    /**
     * Adds a learning goal to the lecture module. Also handles the other side of the relationship.
     *
     * @param learningGoal the learning goal to add
     * @return the lecture module with the learning goal added
     */
    public LectureModule addLearningGoal(LearningGoal learningGoal) {
        this.learningGoals.add(learningGoal);
        learningGoal.getLectureModules().add(this);
        return this;
    }

    /**
     * Removes an learning goal from the lecture module. Also handles the other side of the relationship
     *
     * @param learningGoal the learning goal to remove
     * @return the lecture module with the learning goal removed
     */
    public LectureModule removeLearningGoal(LearningGoal learningGoal) {
        this.learningGoals.remove(learningGoal);
        learningGoal.getLectureModules().remove(this);
        return this;
    }
}
