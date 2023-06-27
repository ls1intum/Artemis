package de.tum.in.www1.artemis.domain.competency;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "learning_path")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class LearningPath extends DomainObject {

    @Column(name = "mastered_competencies")
    private int masteredCompetencies;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties({ "competencies", "prerequisites" })
    private Course course;

    @ManyToMany
    @JoinTable(name = "competency_learning_path", joinColumns = @JoinColumn(name = "learning_path_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "competency_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "exercises", "course", "learningPaths" })
    private Set<Competency> competencies = new HashSet<>();

    public int getMasteredCompetencies() {
        return masteredCompetencies;
    }

    public void setMasteredCompetencies(int masteredCompetencies) {
        this.masteredCompetencies = masteredCompetencies;
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

    public Set<Competency> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(Set<Competency> competencies) {
        this.competencies = competencies;
    }

    public void addCompetency(Competency competency) {
        this.competencies.add(competency);
    }

    public void removeCompetency(Competency competency) {
        this.competencies.remove(competency);
    }

    @Override
    public String toString() {
        return "LearningPath{" + "id=" + getId() + ", user=" + user + ", course=" + course + ", competencies=" + competencies + '}';
    }

    public enum LearningPathSearchColumn {

        ID("id"), STUDENT_LOGIN("student.login");

        private final String mappedColumnName;

        LearningPathSearchColumn(String mappedColumnName) {
            this.mappedColumnName = mappedColumnName;
        }

        public String getMappedColumnName() {
            return mappedColumnName;
        }
    }
}
