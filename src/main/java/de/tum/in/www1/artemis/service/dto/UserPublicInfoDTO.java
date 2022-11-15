package de.tum.in.www1.artemis.service.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;

/**
 * A DTO representing a user with the minimal information allowed to be seen by other users in a course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserPublicInfoDTO {

    private Long id;

    // we need this to differentiate between users with the same name
    private String login;

    private String name;

    private String firstName;

    private String lastName;

    private Boolean isInstructor;

    private Boolean isEditor;

    private Boolean isTeachingAssistant;

    private Boolean isStudent;

    public UserPublicInfoDTO() {
        // Empty constructor needed for Jackson.
    }

    public UserPublicInfoDTO(User user) {
        this.id = user.getId();
        this.login = user.getLogin();
        this.name = user.getName();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();

        this.isInstructor = null;
        this.isEditor = null;
        this.isTeachingAssistant = null;
        this.isStudent = null;
    }

    public static void assignRoleProperties(Course course, User user, UserPublicInfoDTO userPublicInfoDTO) {
        if (course.getStudentGroupName() != null && user.getGroups().contains(course.getStudentGroupName())) {
            userPublicInfoDTO.setIsStudent(true);
        }
        if (course.getTeachingAssistantGroupName() != null && user.getGroups().contains(course.getTeachingAssistantGroupName())) {
            userPublicInfoDTO.setIsTeachingAssistant(true);
        }
        if (course.getInstructorGroupName() != null && user.getGroups().contains(course.getInstructorGroupName())) {
            userPublicInfoDTO.setIsInstructor(true);
        }
        if (course.getEditorGroupName() != null && user.getGroups().contains(course.getEditorGroupName())) {
            userPublicInfoDTO.setIsEditor(true);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Boolean getIsInstructor() {
        return isInstructor;
    }

    public void setIsInstructor(Boolean instructor) {
        isInstructor = instructor;
    }

    public Boolean getIsEditor() {
        return isEditor;
    }

    public void setIsEditor(Boolean editor) {
        isEditor = editor;
    }

    public Boolean getIsTeachingAssistant() {
        return isTeachingAssistant;
    }

    public void setIsTeachingAssistant(Boolean teachingAssistant) {
        isTeachingAssistant = teachingAssistant;
    }

    public Boolean getIsStudent() {
        return isStudent;
    }

    public void setIsStudent(Boolean student) {
        isStudent = student;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof UserPublicInfoDTO that))
            return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserPublicInfoDTO{" + "id=" + id + ", login='" + login + '\'' + ", name='" + name + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\''
                + ", isInstructor=" + isInstructor + ", isEditor=" + isEditor + ", isTeachingAssistant=" + isTeachingAssistant + ", isStudent=" + isStudent + '}';
    }
}
