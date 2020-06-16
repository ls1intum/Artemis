package de.tum.in.www1.artemis.service.ldap;

import static de.tum.in.www1.artemis.config.Constants.TUM_LDAP_MATRIKEL_NUMBER;
import static org.springframework.ldap.query.LdapQueryBuilder.query;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.query.SearchScope;
import org.springframework.stereotype.Service;

@Service
@Profile("ldap")
public class LdapUserService {

    @Value("${artemis.user-management.ldap.base}")
    private String ldapBase;

    @Autowired
    private LdapUserRepository ldapUserRepository;

    public Optional<LdapUserDto> findByUsername(final String username) {
        return ldapUserRepository.findOne(query().base(ldapBase).searchScope(SearchScope.SUBTREE).attributes("cn").where("cn").is(username));
    }

    public Optional<LdapUserDto> findByRegistrationNumber(final String registrationNumber) {
        return ldapUserRepository
                .findOne(query().base(ldapBase).searchScope(SearchScope.SUBTREE).attributes(TUM_LDAP_MATRIKEL_NUMBER).where(TUM_LDAP_MATRIKEL_NUMBER).is(registrationNumber));
    }
}
