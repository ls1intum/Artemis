package de.tum.cit.aet.artemis.service.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;

/**
 * A DTO representing a user with the minimal information allowed to be seen by other users in a course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserPublicInfoDTO {

    @SuppressWarnings("PMD.ShortVariable")
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
    }

    /**
     * Assigns the transient fields isInstructor, isEditor, isTeachingAssistant and isStudent based on the given course and user
     *
     * @param course            the course to check the roles for
     * @param user              the user to check the roles for
     * @param userPublicInfoDTO the DTO to assign the roles to
     */
    public static void assignRoleProperties(Course course, User user, UserPublicInfoDTO userPublicInfoDTO) {
        userPublicInfoDTO.setIsStudent(user.getGroups().contains(course.getStudentGroupName()));
        userPublicInfoDTO.setIsTeachingAssistant(user.getGroups().contains(course.getTeachingAssistantGroupName()));
        userPublicInfoDTO.setIsInstructor(user.getGroups().contains(course.getInstructorGroupName()));
        userPublicInfoDTO.setIsEditor(user.getGroups().contains(course.getEditorGroupName()));
    }

    @SuppressWarnings("PMD.ShortVariable")
    public Long getId() {
        return id;
    }

    @SuppressWarnings("PMD.ShortVariable")
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
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UserPublicInfoDTO that)) {
            return false;
        }
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
