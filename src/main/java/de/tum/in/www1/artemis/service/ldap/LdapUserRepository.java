package de.tum.in.www1.artemis.service.ldap;

import org.springframework.context.annotation.Profile;
import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

@Repository
@Profile("ldap")
public interface LdapUserRepository extends LdapRepository<LdapUserDto> {

}
