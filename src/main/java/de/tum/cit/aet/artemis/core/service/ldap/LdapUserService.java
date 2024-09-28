package de.tum.cit.aet.artemis.core.service.ldap;

import static de.tum.cit.aet.artemis.core.config.Constants.TUM_LDAP_EMAILS;
import static de.tum.cit.aet.artemis.core.config.Constants.TUM_LDAP_MAIN_EMAIL;
import static de.tum.cit.aet.artemis.core.config.Constants.TUM_LDAP_MATRIKEL_NUMBER;
import static org.springframework.ldap.query.LdapQueryBuilder.query;

import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.query.SearchScope;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.ldap.LdapUserRepository;

@Service
@Profile("ldap | ldap-only")
public class LdapUserService {

    private static final Logger log = LoggerFactory.getLogger(LdapUserService.class);

    @Value("${artemis.user-management.ldap.base}")
    private String ldapBase;

    @Value("${artemis.user-management.ldap.allowed-username-pattern:#{null}}")
    private Optional<Pattern> allowedLdapUsernamePattern;

    private final LdapUserRepository ldapUserRepository;

    public LdapUserService(LdapUserRepository ldapUserRepository) {
        this.ldapUserRepository = ldapUserRepository;
    }

    public Optional<LdapUserDto> findByLogin(final String login) {
        return ldapUserRepository.findOne(query().base(ldapBase).searchScope(SearchScope.SUBTREE).where("cn").is(login));
    }

    public Optional<LdapUserDto> findByMainEmail(final String email) {
        return ldapUserRepository.findOne(query().base(ldapBase).searchScope(SearchScope.SUBTREE).where(TUM_LDAP_MAIN_EMAIL).is(email));
    }

    public Optional<LdapUserDto> findByAnyEmail(final String email) {
        return ldapUserRepository.findOne(query().base(ldapBase).searchScope(SearchScope.SUBTREE).where(TUM_LDAP_EMAILS).is(email));
    }

    public Optional<LdapUserDto> findByRegistrationNumber(final String registrationNumber) {
        return ldapUserRepository.findOne(query().base(ldapBase).searchScope(SearchScope.SUBTREE).where(TUM_LDAP_MATRIKEL_NUMBER).is(registrationNumber));
    }

    /**
     * load additional user details from the ldap if it is available: correct firstname, correct lastname and registration number (= matriculation number)
     *
     * @param login the login of the user for which the details should be retrieved
     * @return the found Ldap user details or null if the user cannot be found
     */
    @Nullable
    public LdapUserDto loadUserDetailsFromLdap(@NotNull String login) {
        try {
            Optional<LdapUserDto> ldapUserOptional = findByLogin(login);
            if (ldapUserOptional.isPresent()) {
                LdapUserDto ldapUser = ldapUserOptional.get();
                log.debug("Ldap User {} has registration number: {}", ldapUser.getLogin(), ldapUser.getRegistrationNumber());
                return ldapUserOptional.get();
            }
            else {
                log.warn("Ldap User {} not found", login);
            }
        }
        catch (Exception ex) {
            log.error("Error in LDAP Search", ex);
        }
        return null;
    }

    /**
     * loads the user details from the provided LDAP in case:
     * 1) the allowedUsernamePattern is not specified (means all users should be loaded) or
     * 2) the allowedUsernamePattern is specified and the username matches
     * Example for TUM: ab12cde
     *
     * @param user the user for which the additional details (in particular the registration number should be loaded)
     */
    public void loadUserDetailsFromLdap(User user) {
        if (allowedLdapUsernamePattern.isEmpty() || allowedLdapUsernamePattern.get().matcher(user.getLogin()).matches()) {
            LdapUserDto ldapUserDto = loadUserDetailsFromLdap(user.getLogin());
            if (ldapUserDto != null) {
                if (ldapUserDto.getFirstName() != null) {
                    user.setFirstName(ldapUserDto.getFirstName());
                }
                if (ldapUserDto.getLastName() != null) {
                    user.setLastName(ldapUserDto.getLastName());
                }
                if (ldapUserDto.getEmail() != null) {
                    user.setEmail(ldapUserDto.getEmail());
                }
                if (ldapUserDto.getRegistrationNumber() != null) {
                    user.setRegistrationNumber(ldapUserDto.getRegistrationNumber());
                }
            }
        }
    }
}
