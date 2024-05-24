package de.tum.in.www1.artemis.domain.competency;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.Course;

@Entity
@Table(name = "competency")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public abstract class AbstractCompetency extends BaseCompetency {

    @JsonIgnore
    public static final int DEFAULT_MASTERY_THRESHOLD = 50;

    @JsonIgnore
    public static final int MAX_TITLE_LENGTH = 255;

    @Column(name = "soft_due_date")
    private ZonedDateTime softDueDate;

    @Column(name = "mastery_threshold")
    private Integer masteryThreshold;

    @Column(name = "optional")
    private boolean optional;

    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties({ "competencies", "prerequisites" })
    private Course course;

    @ManyToOne
    @JoinColumn(name = "linked_standardized_competency_id")
    @JsonIgnoreProperties({ "competencies" })
    private StandardizedCompetency linkedStandardizedCompetency;

    @OneToMany(mappedBy = "competency", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties({ "user", "competency" })
    private Set<CompetencyProgress> userProgress = new HashSet<>();

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties({ "competencies", "course" })
    private Set<LearningPath> learningPaths = new HashSet<>();

    public AbstractCompetency() {
    }

    public AbstractCompetency(String title, String description, ZonedDateTime softDueDate, Integer masteryThreshold, CompetencyTaxonomy taxonomy, boolean optional) {
        super(title, description, taxonomy);
        this.softDueDate = softDueDate;
        this.masteryThreshold = masteryThreshold;
        this.optional = optional;
    }

    public ZonedDateTime getSoftDueDate() {
        return softDueDate;
    }

    public void setSoftDueDate(ZonedDateTime softDueDate) {
        this.softDueDate = softDueDate;
    }

    public Integer getMasteryThreshold() {
        return masteryThreshold;
    }

    public void setMasteryThreshold(Integer masteryThreshold) {
        this.masteryThreshold = masteryThreshold;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public StandardizedCompetency getLinkedStandardizedCompetency() {
        return linkedStandardizedCompetency;
    }

    public void setLinkedStandardizedCompetency(StandardizedCompetency linkedStandardizedCompetency) {
        this.linkedStandardizedCompetency = linkedStandardizedCompetency;
    }

    public Set<CompetencyProgress> getUserProgress() {
        return userProgress;
    }

    public void setUserProgress(Set<CompetencyProgress> userProgress) {
        this.userProgress = userProgress;
    }

    public Set<LearningPath> getLearningPaths() {
        return learningPaths;
    }

    public void setLearningPaths(Set<LearningPath> learningPaths) {
        this.learningPaths = learningPaths;
    }
}
