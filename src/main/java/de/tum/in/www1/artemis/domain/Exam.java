package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

@Entity
@Table(name = "exam")
public class Exam {

    // region CONSTRUCTORS
    // -----------------------------------------------------------------------------------------------------------------
    // no arg constructor required for jpa
    public Exam() {
    }

    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region BASIC PROPERTIES
    // -----------------------------------------------------------------------------------------------------------------
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", unique = true, nullable = false)
    private String title;

    /**
     * student can see the exam in the UI from {@link #visibleDate} date onwards
     */
    @Column(name = "visible_date")
    private ZonedDateTime visibleDate;

    /**
     * student can start working on exam from {@link #startDate}
     */
    @Column(name = "start_date")
    private ZonedDateTime startDate;

    /**
     * student can work on exam until {@link #endDate}
     */
    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @Column(name = "start_text")
    private String startText;

    @Column(name = "end_text")
    private String endText;

    @Column(name = "confirmation_start_text")
    private String confirmationStartText;

    @Column(name = "confirmation_end_text")
    private String confirmationEndText;

    @Column(name = "max_points")
    private Integer maxPoints;

    /**
     * From all exercise groups connected to the exam, {@link #numberOfExercisesInExam} are randomly
     * chosen when generating the specific exam for the {@link #registeredUsers}
     */
    @Column(name = "number_of_exercises_in_exam")
    private Integer numberOfExercisesInExam;

    @Column(name = "hasExerciseGroupOrder")
    private Boolean hasExerciseGroupOrder;

    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region RELATIONSHIPS
    // -----------------------------------------------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    // -----------------------------------------------------------------------------------------------------------------
    @OneToMany(mappedBy = "exam")
    @OrderColumn
    private List<ExerciseGroup> exerciseGroups = new ArrayList<>();

    public List<ExerciseGroup> getExerciseGroups() {
        return exerciseGroups;
    }

    public void setExerciseGroups(List<ExerciseGroup> exerciseGroups) {
        this.exerciseGroups = exerciseGroups;
    }

    public void addExerciseGroup(ExerciseGroup exerciseGroup) {
        this.exerciseGroups.add(exerciseGroup);
        if (exerciseGroup.getExam() != this) {
            exerciseGroup.setExam(this);
        }
    }

    public void removeExerciseGroup(ExerciseGroup exerciseGroup) {
        this.exerciseGroups.remove(exerciseGroup);
        if (exerciseGroup.getExam() == this) {
            exerciseGroup.setExam(null);
        }
    }
    // -----------------------------------------------------------------------------------------------------------------

    @OneToMany(mappedBy = "exam")
    private Set<StudentExam> studentExams = new HashSet<>();

    public Set<StudentExam> getStudentExams() {
        return studentExams;
    }

    public void setStudentExams(Set<StudentExam> studentExams) {
        this.studentExams = studentExams;
    }

    public void addStudentExam(StudentExam studentExam) {
        this.studentExams.add(studentExam);
        if (studentExam.getExam() != this) {
            studentExam.setExam(this);
        }
    }

    public void removeStudentExam(StudentExam studentExam) {
        this.studentExams.remove(studentExam);
        if (studentExam.getExam() == this) {
            studentExam.setExam(null);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @ManyToMany
    @JoinTable(name = "exam_jhi_user", joinColumns = @JoinColumn(name = "exam_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "student_id", referencedColumnName = "id"))
    private Set<User> registeredUsers = new HashSet<>();

    public Set<User> getRegisteredUsers() {
        return registeredUsers;
    }

    public void setRegisteredUsers(Set<User> registeredUsers) {
        this.registeredUsers = registeredUsers;
    }

    public void addUser(User user) {
        this.registeredUsers.add(user);
    }

    public void removeUser(User user) {
        this.registeredUsers.remove(user);
    }
    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region SIMPLE GETTERS AND SETTERS
    // -----------------------------------------------------------------------------------------------------------------

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

    public ZonedDateTime getVisibleDate() {
        return visibleDate;
    }

    public void setVisibleDate(ZonedDateTime visibleDate) {
        this.visibleDate = visibleDate;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public String getStartText() {
        return startText;
    }

    public void setStartText(String startText) {
        this.startText = startText;
    }

    public String getEndText() {
        return endText;
    }

    public void setEndText(String endText) {
        this.endText = endText;
    }

    public String getConfirmationStartText() {
        return confirmationStartText;
    }

    public void setConfirmationStartText(String confirmationStartText) {
        this.confirmationStartText = confirmationStartText;
    }

    public String getConfirmationEndText() {
        return confirmationEndText;
    }

    public void setConfirmationEndText(String confirmationEndText) {
        this.confirmationEndText = confirmationEndText;
    }

    public Integer getMaxPoints() {
        return this.maxPoints;
    }

    public void setMaxPoints(Integer maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Integer getNumberOfExercisesInExam() {
        return numberOfExercisesInExam;
    }

    public void setNumberOfExercisesInExam(Integer numberOfExercisesInExam) {
        this.numberOfExercisesInExam = numberOfExercisesInExam;
    }

    public Boolean getHasExerciseGroupOrder() {
        return hasExerciseGroupOrder;
    }

    public void setHasExerciseGroupOrder(Boolean hasExerciseGroupOrder) {
        this.hasExerciseGroupOrder = hasExerciseGroupOrder;
    }

    // endregion
    // -----------------------------------------------------------------------------------------------------------------

    // region HASHCODE AND EQUAL
    // -----------------------------------------------------------------------------------------------------------------
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Exam exam = (Exam) o;
        return Objects.equals(getId(), exam.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
    // endregion
    // -----------------------------------------------------------------------------------------------------------------

}
