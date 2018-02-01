package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * A Exercise.
 */
@Entity
@Table(name = "exercise")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Exercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "base_project_key")
    private String baseProjectKey;

    @Column(name = "base_repository_slug")
    private String baseRepositorySlug;

    @Column(name = "base_build_plan_slug")
    private String baseBuildPlanSlug;

    @Column(name = "release_date")
    private ZonedDateTime releaseDate;

    @Column(name = "due_date")
    private ZonedDateTime dueDate;

    @ManyToOne
    private Course course;

    @OneToMany(mappedBy = "exercise")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Participation> participations = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBaseProjectKey() {
        return baseProjectKey;
    }

    public void setBaseProjectKey(String baseProjectKey) {
        this.baseProjectKey = baseProjectKey;
    }

    public String getBaseRepositorySlug() {
        return baseRepositorySlug;
    }

    public void setBaseRepositorySlug(String baseRepositorySlug) {
        this.baseRepositorySlug = baseRepositorySlug;
    }

    public String getBaseBuildPlanSlug() {
        return baseBuildPlanSlug;
    }

    public void setBaseBuildPlanSlug(String baseBuildPlanSlug) {
        this.baseBuildPlanSlug = baseBuildPlanSlug;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<Participation> getParticipations() {
        return participations;
    }

    public void setParticipations(Set<Participation> participations) {
        this.participations = participations;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Exercise exercise = (Exercise) o;
        if (exercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), exercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Exercise{" +
            "id=" + getId() +
            ", title='" + getTitle() + "'" +
            ", baseProjectKey='" + getBaseProjectKey() + "'" +
            ", baseRepositorySlug='" + getBaseRepositorySlug() + "'" +
            ", baseBuildPlanSlug='" + getBaseBuildPlanSlug() + "'" +
            ", releaseDate='" + getReleaseDate() + "'" +
            ", dueDate='" + getDueDate() + "'" +
            "}";
    }
}
