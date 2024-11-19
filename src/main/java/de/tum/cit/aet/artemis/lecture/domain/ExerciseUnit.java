package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Entity
@DiscriminatorValue("E")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseUnit extends LectureUnit {

    // Note: Name, release date and competencies will always be taken from associated exercise
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exercise_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Exercise exercise;

    // Competency links are not persisted in this entity but only in the exercise itself
    @Transient
    @JsonSerialize
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties("lectureUnit")
    private Set<CompetencyLectureUnitLink> competencyLinks = new HashSet<>();

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public boolean isVisibleToStudents() {
        return exercise == null || exercise.isVisibleToStudents();
    }

    @Override
    public String getName() {
        return exercise == null ? null : exercise.getTitle();
    }

    @Override
    public void setName(String name) {
        // Should be set in associated exercise
    }

    @Override
    public ZonedDateTime getReleaseDate() {
        return exercise == null ? null : exercise.getReleaseDate();
    }

    @Override
    public void setReleaseDate(ZonedDateTime releaseDate) {
        // Should be set in associated exercise
    }

    @Override
    @JsonSerialize
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties("lectureUnit")
    public Set<CompetencyLectureUnitLink> getCompetencyLinks() {
        return competencyLinks;
    }

    @Override
    public void setCompetencyLinks(Set<CompetencyLectureUnitLink> competencyLinks) {
        this.competencyLinks = competencyLinks;
    }

    /**
     * Ensure that we do not accidentally persist values taken from the corresponding exercise
     */
    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        this.name = null;
        this.releaseDate = null;
    }

    // IMPORTANT NOTICE: The following string has to be consistent with the one defined in LectureUnit.java
    @Override
    public String getType() {
        return "exercise";
    }
}
