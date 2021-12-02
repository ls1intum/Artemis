package de.tum.in.www1.artemis.service.ldap;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_LDAP;

import org.springframework.context.annotation.Profile;
import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

@Repository
@Profile(SPRING_PROFILE_LDAP)
public interface LdapUserRepository extends LdapRepository<LdapUserDto> {

}
