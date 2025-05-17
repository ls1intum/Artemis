package de.tum.cit.aet.artemis.core.service.ldap;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LDAP;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.ldap.repository.config.EnableLdapRepositories;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;

@Configuration
@Lazy
@Profile(PROFILE_LDAP)
@EnableLdapRepositories(basePackages = "de.tum.cit.aet.artemis.core.repository.ldap")
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
    @Lazy
    public LdapContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapUrl);
        contextSource.setUserDn(ldapUserDn);
        contextSource.setPassword(ldapPassword);
        return contextSource;
    }

    @Bean
    @Lazy
    public SpringSecurityLdapTemplate ldapTemplate() {
        return new SpringSecurityLdapTemplate(contextSource());
    }
}
