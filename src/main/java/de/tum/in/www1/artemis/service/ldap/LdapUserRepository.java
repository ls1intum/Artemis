package de.tum.in.www1.artemis.service.ldap;

import org.springframework.context.annotation.Profile;
import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_LDAP;

@Repository
@Profile(SPRING_PROFILE_LDAP)
public interface LdapUserRepository extends LdapRepository<LdapUserDto> {

}
