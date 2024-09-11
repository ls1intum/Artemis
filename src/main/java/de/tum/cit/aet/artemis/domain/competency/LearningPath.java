package de.tum.cit.aet.artemis.domain.competency;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.User;

@Entity
@Table(name = "learning_path")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class LearningPath extends DomainObject {

    /**
     * number in [0, 100] representing the progress in percentage
     */
    @Column(name = "progress")
    private int progress;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties({ "competencies", "prerequisites", "exercises" })
    private Course course;

    @ManyToMany
    @JoinTable(name = "competency_learning_path", joinColumns = @JoinColumn(name = "learning_path_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "competency_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "exercises", "course", "learningPaths" })
    private Set<CourseCompetency> competencies = new HashSet<>();

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<CourseCompetency> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(Set<CourseCompetency> competencies) {
        this.competencies = competencies;
    }

    public void addCompetency(CourseCompetency competency) {
        this.competencies.add(competency);
    }

    public void addCompetencies(Set<CourseCompetency> competencies) {
        this.competencies.addAll(competencies);
    }

    public void removeCompetency(CourseCompetency competency) {
        this.competencies.remove(competency);
    }

    @Override
    public String toString() {
        return "LearningPath{" + "id=" + getId() + ", user=" + user + ", course=" + course + ", competencies=" + competencies + '}';
    }
}
