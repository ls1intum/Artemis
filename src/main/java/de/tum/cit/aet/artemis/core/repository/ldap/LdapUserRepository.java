package de.tum.cit.aet.artemis.core.repository.ldap;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.config.LdapEnabled;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserDto;

@Lazy
@Repository
@Conditional(LdapEnabled.class)
public interface LdapUserRepository extends LdapRepository<LdapUserDto> {

}
