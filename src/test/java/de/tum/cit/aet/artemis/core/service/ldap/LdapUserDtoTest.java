package de.tum.cit.aet.artemis.core.service.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.junit.jupiter.api.Test;

class LdapUserDtoTest {

    @Test
    void testLoginGetterSetter() {
        LdapUserDto dto = new LdapUserDto();
        dto.setLogin("testuser");
        assertThat(dto.getLogin()).isEqualTo("testuser");
    }

    @Test
    void testLoginBuilder() {
        LdapUserDto dto = new LdapUserDto().login("testuser");
        assertThat(dto.getLogin()).isEqualTo("testuser");
    }

    @Test
    void testRegistrationNumberBuilder() {
        LdapUserDto dto = new LdapUserDto().registrationNumber("12345678");
        assertThat(dto.getRegistrationNumber()).isEqualTo("12345678");
    }

    @Test
    void testFirstNameBuilder() {
        LdapUserDto dto = new LdapUserDto().firstName("John");
        assertThat(dto.getFirstName()).isEqualTo("John");
    }

    @Test
    void testLastNameBuilder() {
        LdapUserDto dto = new LdapUserDto().lastName("Doe");
        assertThat(dto.getLastName()).isEqualTo("Doe");
    }

    @Test
    void testEmailBuilder() {
        LdapUserDto dto = new LdapUserDto().email("john.doe@example.com");
        assertThat(dto.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void testUidGetterSetter() throws InvalidNameException {
        LdapUserDto dto = new LdapUserDto();
        LdapName ldapName = new LdapName("cn=testuser,ou=users,o=company");
        dto.setUid(ldapName);
        assertThat(dto.getUid()).isEqualTo(ldapName);
    }

    @Test
    void testBuilderChaining() {
        LdapUserDto dto = new LdapUserDto().login("testuser").firstName("John").lastName("Doe").email("john.doe@example.com").registrationNumber("12345678");

        assertThat(dto.getLogin()).isEqualTo("testuser");
        assertThat(dto.getFirstName()).isEqualTo("John");
        assertThat(dto.getLastName()).isEqualTo("Doe");
        assertThat(dto.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(dto.getRegistrationNumber()).isEqualTo("12345678");
    }
}
