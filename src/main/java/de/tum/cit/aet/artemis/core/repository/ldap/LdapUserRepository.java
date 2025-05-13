package de.tum.cit.aet.artemis.core.repository.ldap;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LDAP_OR_LDAP_ONLY;

import org.springframework.context.annotation.Profile;
import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.service.ldap.LdapUserDto;

@Repository
@Profile(PROFILE_LDAP_OR_LDAP_ONLY)
public interface LdapUserRepository extends LdapRepository<LdapUserDto> {

}
