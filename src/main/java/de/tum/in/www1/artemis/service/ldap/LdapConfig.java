package de.tum.in.www1.artemis.service.ldap;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_LDAP;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.ldap.repository.config.EnableLdapRepositories;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
@Profile(SPRING_PROFILE_LDAP)
@EnableLdapRepositories
public class LdapConfig {

    @Value("${artemis.user-management.ldap.url}")
    private String ldapUrl;

    @Value("${artemis.user-management.ldap.user-dn}")
    private String ldapUserDn;

    @Value("${artemis.user-management.ldap.password}")
    private String ldapPassword;

    /**
     * @return configure the ldap repository using the values from the yml file
     */
    @Bean
    public LdapContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapUrl);
        contextSource.setUserDn(ldapUserDn);
        contextSource.setPassword(ldapPassword);
        return contextSource;
    }

    @Bean
    public LdapTemplate ldapTemplate() {
        return new LdapTemplate(contextSource());
    }
}
