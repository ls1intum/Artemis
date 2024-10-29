package de.tum.cit.aet.artemis.core.service.ldap;

import static de.tum.cit.aet.artemis.core.config.Constants.TUM_LDAP_MAIN_EMAIL;
import static de.tum.cit.aet.artemis.core.config.Constants.TUM_LDAP_MATRIKEL_NUMBER;

import javax.naming.Name;

import org.springframework.context.annotation.Profile;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

@Entry(base = "ou=users", objectClasses = { "imdPerson" })
@Profile("ldap | ldap-only")
// TODO: double check if we can use a Record here
public final class LdapUserDto {

    @Id
    private Name uid;

    @Attribute(name = "uid")
    private String login;

    @Attribute(name = TUM_LDAP_MATRIKEL_NUMBER)
    private String registrationNumber;

    @Attribute(name = "imVorname")
    private String firstName;

    @Attribute(name = "sn")
    private String lastName;

    @Attribute(name = TUM_LDAP_MAIN_EMAIL)
    private String email;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public LdapUserDto login(String login) {
        this.login = login;
        return this;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public LdapUserDto registrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
        return this;
    }

    public String getFirstName() {
        return firstName;
    }

    public LdapUserDto firstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public LdapUserDto lastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public LdapUserDto email(String email) {
        this.email = email;
        return this;
    }

    public Name getUid() {
        return uid;
    }

    public void setUid(Name uid) {
        this.uid = uid;
    }
}
