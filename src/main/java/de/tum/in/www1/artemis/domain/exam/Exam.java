package de.tum.in.www1.artemis.domain.exam;

import java.time.ZonedDateTime;
import java.util.HashSet;
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
import javax.persistence.Table;

import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ExerciseGroup;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "EXAM")
public class Exam extends AbstractAuditingEntity {

    // region CONSTRUCTORS
    // -----------------------------------------------------------------------------------------------------------------
    // no arg constructor required for jpa
    public Exam() {
    }

    public Exam(Long id, String title, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, String startText, String endText, String confirmationStartText,
            String confirmationEndText, Integer numberOfExerciseGroups, Course course, Set<ExerciseGroup> exerciseGroups, Set<StudentExam> studentExams,
            Set<User> registeredUsers) {
        this.id = id;
        this.title = title;
        this.visibleDate = visibleDate;
        this.startDate = startDate;
        this.endDate = endDate;
        this.startText = startText;
        this.endText = endText;
        this.confirmationStartText = confirmationStartText;
        this.confirmationEndText = confirmationEndText;
        this.numberOfExerciseGroups = numberOfExerciseGroups;
        this.course = course;
        this.exerciseGroups = exerciseGroups;
        this.studentExams = studentExams;
        this.registeredUsers = registeredUsers;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region BASIC PROPERTIES
    // -----------------------------------------------------------------------------------------------------------------
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TITLE", unique = true, nullable = false)
    private String title;

    /**
     * student can see the exam in the UI from {@link #visibleDate} date onwards
     */
    @Column(name = "VISIBLE_DATE")
    private ZonedDateTime visibleDate;

    /**
     * student can start working on exam from {@link #startDate}
     */
    @Column(name = "START_DATE")
    private ZonedDateTime startDate;

    /**
     * student can work on exam until {@link #endDate}
     */
    @Column(name = "END_DATE")
    private ZonedDateTime endDate;

    @Column(name = "START_TEXT")
    private String startText;

    @Column(name = "END_TEXT")
    private String endText;

    @Column(name = "CONFIRMATION_START_TEXT")
    private String confirmationStartText;

    @Column(name = "CONFIRMATION_END_TEXT")
    private String confirmationEndText;

    /**
     * From all exercise groups connected to the exam, {@link #numberOfExerciseGroups} are randomly
     * chosen when generating the specific exam for the {@link #registeredUsers}
     */
    @Column(name = "NUMBER_OF_EXERCISE_GROUPS")
    private Integer numberOfExerciseGroups;

    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region RELATIONSHIPS
    // -----------------------------------------------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COURSE_ID")
    private Course course;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        if (this.course != null) {
            this.course.removeExam(this);
        }

        this.course = course;
        if (!course.getExams().contains(this)) {
            course.getExams().add(this);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @OneToMany(mappedBy = "exam")
    private Set<ExerciseGroup> exerciseGroups = new HashSet<>();

    public Set<ExerciseGroup> getExerciseGroups() {
        return exerciseGroups;
    }

    public void setExerciseGroups(Set<ExerciseGroup> exerciseGroups) {
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
    @JoinTable(name = "EXAM_JHI_USER", joinColumns = @JoinColumn(name = "EXAM_ID", referencedColumnName = "ID"), inverseJoinColumns = @JoinColumn(name = "STUDENT_ID", referencedColumnName = "ID"))
    private Set<User> registeredUsers = new HashSet<>();

    public Set<User> getRegisteredUsers() {
        return registeredUsers;
    }

    public void setRegisteredUsers(Set<User> registeredUsers) {
        this.registeredUsers = registeredUsers;
    }

    public void addUser(User user) {
        this.registeredUsers.add(user);
        user.getExams().remove(this);
    }

    public void removeUser(User user) {
        this.registeredUsers.remove(user);
        user.getExams().remove(this);
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

    public Integer getNumberOfExerciseGroups() {
        return numberOfExerciseGroups;
    }

    public void setNumberOfExerciseGroups(Integer numberOfExerciseGroups) {
        this.numberOfExerciseGroups = numberOfExerciseGroups;
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
        return Objects.equals(title, exam.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title);
    }
    // endregion
    // -----------------------------------------------------------------------------------------------------------------

}
