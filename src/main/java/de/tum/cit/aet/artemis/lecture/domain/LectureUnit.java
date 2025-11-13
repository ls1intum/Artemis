package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "lecture_unit")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("L")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Annotation necessary to distinguish between concrete implementations of lecture-content when deserializing from JSON
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = AttachmentVideoUnit.class, name = "attachment"),
    @JsonSubTypes.Type(value = ExerciseUnit.class, name = "exercise"),
    @JsonSubTypes.Type(value = TextUnit.class, name = "text"),
    @JsonSubTypes.Type(value = OnlineUnit.class, name = "online")
})
// @formatter:on
public abstract class LectureUnit extends DomainObject implements LearningObject {

    @Transient
    private boolean completed;

    @Column(name = "name")
    protected String name;

    @Column(name = "release_date")
    protected ZonedDateTime releaseDate;

    // internal order field, NOT exposed in public API
    @JsonIgnore
    @Column(name = "lecture_unit_order", nullable = false)
    private int lectureUnitOrder;   // package-private, no getter/setter

    // Must only be used by Lecture to set the order of its lecture units before persisting
    // TODO: write an architecture test to ensure this
    void setLectureUnitOrder(int order) {
        this.lectureUnitOrder = order;
    }

    @ManyToOne(optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Lecture lecture;

    @OneToMany(mappedBy = "lectureUnit", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("lectureUnit")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    protected Set<CompetencyLectureUnitLink> competencyLinks = new HashSet<>();

    @OneToMany(mappedBy = "lectureUnit", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore // important, so that the completion status of other users do not leak to anyone
    private Set<LectureUnitCompletion> completedUsers = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.strip() : null;
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

    @Override
    public Set<CompetencyLectureUnitLink> getCompetencyLinks() {
        return competencyLinks;
    }

    public void setCompetencyLinks(Set<CompetencyLectureUnitLink> competencyLinks) {
        this.competencyLinks = competencyLinks;
    }

    // NOTE: we explicitly do not add LectureTranscription here to avoid Hibernate issues because of its OneToOne relationship which is EAGER and cannot be set to LAZY

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

    /**
     * Checks if the lecture unit is visible to the students.
     * A lecture unit is visible to the students if the lecture is visible to the students and the release date is null or in the past.
     *
     * @return true if the lecture unit is visible to the students, false otherwise
     */
    @JsonProperty("visibleToStudents")
    public boolean isVisibleToStudents() {
        /* The visibleDate property of the Lecture entity is deprecated. We’re keeping the related logic temporarily to monitor for user feedback before full removal */
        /* TODO: #11479 - remove the commented out code OR comment back in */
        // if (lecture == null || !lecture.isVisibleToStudents()) {
        // return false;
        // }

        if (releaseDate == null) {
            return true;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

    public boolean isCompletedFor(User user) {
        return getCompletedUsers().stream().map(LectureUnitCompletion::getUser).anyMatch(user1 -> user1.getId().equals(user.getId()));
    }

    @Override
    public Optional<ZonedDateTime> getCompletionDate(User user) {
        return getCompletedUsers().stream().filter(completion -> completion.getUser().getId().equals(user.getId())).map(LectureUnitCompletion::getCompletedAt).findFirst();
    }

    @PrePersist
    @PreUpdate
    private void updateLectureUnitOrder() {
        if (lecture == null) {
            return; // should not really happen due to nullable=false, but safe
        }
        lecture.updateLectureUnitOrder();
    }

    // Used to distinguish the type when used in a DTO, e.g., LectureUnitForLearningPathNodeDetailsDTO.
    public abstract String getType();

    @Override
    public String toString() {
        return "LectureUnit{" + "id=" + getId() + ", type=" + getType() + ", name='" + name + "'" + ", releaseDate=" + releaseDate + ", lectureUnitOrder=" + lectureUnitOrder + '}';
    }
}
