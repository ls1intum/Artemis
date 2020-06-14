package de.tum.in.www1.artemis.service.ldap;

import static de.tum.in.www1.artemis.config.Constants.TUM_LDAP_MATRIKEL_NUMBER;

import javax.naming.Name;

import org.springframework.context.annotation.Profile;
import org.springframework.ldap.odm.annotations.*;

@Entry(base = "ou=users", objectClasses = { "imdPerson" })
@Profile("ldap")
final public class LdapUserDto {

    @Id
    private Name uid;

    private @Attribute(name = "uid") String username;

    private @Attribute(name = TUM_LDAP_MATRIKEL_NUMBER) String registrationNumber;

    private @Attribute(name = "imVorname") String firstName;

    private @Attribute(name = "sn") String lastName;

    private @Attribute(name = "imHauptEMail") String email;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
