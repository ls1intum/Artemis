package de.tum.in.www1.artemis.service.ldap;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.query.SearchScope;
import org.springframework.stereotype.Service;

@Service
@Profile("ldap")
public class LdapUserService {

    @Autowired
    private LdapUserRepository userRepository;

    public Optional<LdapUserDto> findOne(final String username) {
        return userRepository.findOne(query().base("ou=users,ou=data,ou=prod,ou=iauth,dc=tum,dc=de").searchScope(SearchScope.SUBTREE).attributes("cn").where("cn").is(username));
    }
}
