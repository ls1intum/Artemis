package de.tum.cit.aet.artemis.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.service.ldap.LdapUserDto;

@Repository
@Profile("ldap | ldap-only")
public interface LdapUserRepository extends LdapRepository<LdapUserDto> {

}
