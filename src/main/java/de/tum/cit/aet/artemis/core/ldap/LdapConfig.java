package de.tum.cit.aet.artemis.core.ldap;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LDAP;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@EnableLdapRepositories(basePackages = "de.tum.cit.aet.artemis.core.ldap")
public class LdapConfig {

    private static final Logger log = LoggerFactory.getLogger(LdapConfig.class);

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
        log.debug("create ldap context source");
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapUrl);
        contextSource.setUserDn(ldapUserDn);
        contextSource.setPassword(ldapPassword);
        return contextSource;
    }

    @Bean
    @Lazy
    public SpringSecurityLdapTemplate ldapTemplate() {
        log.debug("create ldap template");
        return new SpringSecurityLdapTemplate(contextSource());
    }
}
