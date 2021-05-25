package de.tum.in.www1.metis.domain;

import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;

/**
 * A root post, i.e., start of METIS Thread.
 */
@Entity
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Table(name = "root_post")
public class RootPost extends Post {

    @Size(max = 200)
    @Column(name = "title")
    private String title;

    @OneToMany(mappedBy = "root_post", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("rootPost")
    private Set<AnswerPost> answers;

    @Column(name = "visible_for_students")
    private Boolean visibleForStudents;

    @ManyToOne
    private Exercise exerciseContext;

    @ManyToOne
    private Lecture lectureContext;

    @ManyToOne
    private Course courseContext;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_wide_context")
    private CourseWideContext courseWideContext;

    public Course getCourseContext() {
        return courseContext;
    }

    public void setCourseContext(Course courseContext) {
        this.courseContext = courseContext;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Set<AnswerPost> getAnswers() {
        return answers;
    }

    public void setAnswers(Set<AnswerPost> answers) {
        this.answers = answers;
    }

    public Boolean getVisibleForStudents() {
        return visibleForStudents;
    }

    public void setVisibleForStudents(Boolean visibleForStudents) {
        this.visibleForStudents = visibleForStudents;
    }

    public Exercise getExerciseContext() {
        return exerciseContext;
    }

    public void setExerciseContext(Exercise exerciseContext) {
        this.exerciseContext = exerciseContext;
    }

    public Lecture getLectureContext() {
        return lectureContext;
    }

    public void setLectureContext(Lecture lectureContext) {
        this.lectureContext = lectureContext;
    }

    public CourseWideContext getCourseWideContext() {
        return courseWideContext;
    }

    public void setCourseWideContext(CourseWideContext courseWideContext) {
        this.courseWideContext = courseWideContext;
    }

    public void addAnswer(AnswerPost answerPost) {
        answers.add(answerPost);
        answerPost.setRootPost(this);
    }

    public void removeAnswer(AnswerPost answerPost) {
        answers.remove(answerPost);
    }

    /**
     * Convenience method to retrieve the relevant course from linked context.
     *
     * @return related Course object
     */
    @Override
    public Course getCourse() {
        if (getLectureContext() != null) {
            return getLectureContext().getCourse();
        }
        else if (getExerciseContext() != null) {
            return getExerciseContext().getCourseViaExerciseGroupOrCourseMember();
        }
        else if (getCourseContext() != null) {
            return getCourseContext();
        }
        return null;
    }
}
