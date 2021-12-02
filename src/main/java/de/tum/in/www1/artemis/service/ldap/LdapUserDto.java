package de.tum.in.www1.artemis.service.ldap;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_LDAP;
import static de.tum.in.www1.artemis.config.Constants.TUM_LDAP_MATRIKEL_NUMBER;

import javax.naming.Name;

import org.springframework.context.annotation.Profile;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

@Entry(base = "ou=users", objectClasses = { "imdPerson" })
@Profile(SPRING_PROFILE_LDAP)
final public class LdapUserDto {

    @Id
    private Name uid;

    @Attribute(name = "uid")
    private String username;

    @Attribute(name = TUM_LDAP_MATRIKEL_NUMBER)
    private String registrationNumber;

    @Attribute(name = "imVorname")
    private String firstName;

    @Attribute(name = "sn")
    private String lastName;

    @Attribute(name = "imHauptEMail")
    private String email;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LdapUserDto username(String username) {
        this.username = username;
        return this;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public LdapUserDto registrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
        return this;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public LdapUserDto firstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LdapUserDto lastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LdapUserDto email(String email) {
        this.email = email;
        return this;
    }
}
