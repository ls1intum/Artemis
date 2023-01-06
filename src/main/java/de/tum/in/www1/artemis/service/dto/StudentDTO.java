package de.tum.in.www1.artemis.service.dto;

import java.util.Objects;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentDTO {

    @Size(max = 50)
    private String login;

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    @Size(max = 10)
    private String registrationNumber;

    @Email
    @Size(max = 100)
    private String email;

    public StudentDTO(String login, String firstName, String lastName, String registrationNumber) {
        this.login = login;
        this.firstName = firstName;
        this.lastName = lastName;
        this.registrationNumber = registrationNumber;
    }

    public StudentDTO(User user) {
        this(user.getLogin(), user.getFirstName(), user.getLastName(), user.getRegistrationNumber());
    }

    public StudentDTO() {
        // for jackson
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
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

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public StudentDTO registrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        StudentDTO that = (StudentDTO) obj;
        return Objects.equals(registrationNumber, that.registrationNumber) || Objects.equals(login, that.login) || Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registrationNumber) ^ Objects.hash(login) ^ Objects.hash(email);
    }

    @Override
    public String toString() {
        return "StudentDTO{" + "login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", registrationNumber='" + registrationNumber
                + '\'' + ", email='" + email + '\'' + '}';
    }

    @JsonIgnore
    public String toDatabaseString() {
        return "Student: login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", registrationNumber='" + registrationNumber + '\''
                + ", email='" + email + '\'';
    }
}
