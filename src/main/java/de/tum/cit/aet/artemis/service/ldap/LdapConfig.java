package de.tum.cit.aet.artemis.service.ldap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.ldap.repository.config.EnableLdapRepositories;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;

@Configuration
@Profile("ldap | ldap-only")
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
    public SpringSecurityLdapTemplate ldapTemplate() {
        return new SpringSecurityLdapTemplate(contextSource());
    }
}
