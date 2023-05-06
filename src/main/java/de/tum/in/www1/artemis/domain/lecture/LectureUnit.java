package de.tum.in.www1.artemis.domain.lecture;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

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
        @JsonSubTypes.Type(value = TextUnit.class, name = "text"), @JsonSubTypes.Type(value = VideoUnit.class, name = "video"),
        @JsonSubTypes.Type(value = OnlineUnit.class, name = "online") })
public abstract class LectureUnit extends DomainObject implements LearningObject {

    @Transient
    private boolean completed;

    @Column(name = "name")
    protected String name;

    @Column(name = "release_date")
    protected ZonedDateTime releaseDate;

    // This is explicitly required by Hibernate for the indexed collection (OrderColumn)
    // https://docs.jboss.org/hibernate/stable/annotations/reference/en/html_single/#entity-hibspec-collection-extratype-indexbidir
    @Column(name = "lecture_unit_order")
    @Transient
    private int order;

    @ManyToOne
    @JoinColumn(name = "lecture_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Lecture lecture;

    @ManyToMany
    @JoinTable(name = "learning_goal_lecture_unit", joinColumns = @JoinColumn(name = "lecture_unit_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"))
    @OrderBy("title")
    @JsonIgnoreProperties({ "lectureUnits", "course" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    protected Set<LearningGoal> learningGoals = new HashSet<>();

    @OneToMany(mappedBy = "lectureUnit", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore // important, so that the completion status of other users do not leak to anyone
    private Set<LectureUnitCompletion> completedUsers = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.strip() : null;
    }

    public int getOrder() {
        return order;
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

    public Set<LearningGoal> getCompetencies() {
        return learningGoals;
    }

    public void setLearningGoals(Set<LearningGoal> learningGoals) {
        this.learningGoals = learningGoals;
    }

    @JsonIgnore(false)
    @JsonProperty("completed")
    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Set<LectureUnitCompletion> getCompletedUsers() {
        return completedUsers;
    }

    public void setCompletedUsers(Set<LectureUnitCompletion> completedUsers) {
        this.completedUsers = completedUsers;
    }

    @JsonProperty("visibleToStudents")
    public boolean isVisibleToStudents() {
        if (releaseDate == null) {
            return true;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

    @Override
    public boolean isCompletedFor(User user) {
        return getCompletedUsers().stream().map(LectureUnitCompletion::getUser).anyMatch(user1 -> user1.getId().equals(user.getId()));
    }

    @Override
    public Optional<ZonedDateTime> getCompletionDate(User user) {
        return getCompletedUsers().stream().filter(completion -> completion.getUser().getId().equals(user.getId())).map(LectureUnitCompletion::getCompletedAt).findFirst();
    }
}
