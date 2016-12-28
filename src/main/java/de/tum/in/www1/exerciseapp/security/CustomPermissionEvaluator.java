package de.tum.in.www1.exerciseapp.security;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.service.GitService;
import de.tum.in.www1.exerciseapp.service.ParticipationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.Serializable;



/**
 * Created by Josias Montag on 21.12.16.
 */
@Component
@ComponentScan("de.tum.in.www1.exerciseapp.*")
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final Logger log = LoggerFactory.getLogger(GitService.class);

    @Inject @Lazy
    private ParticipationService participationService;

    private GrantedAuthority adminAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN);
    private GrantedAuthority taAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.TEACHING_ASSISTANT);


    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        if ((auth == null) || (targetDomainObject == null) || !(permission instanceof String)){
            return false;
        }
        String targetType = targetDomainObject.getClass().getSimpleName().toUpperCase();

        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        if ((auth == null) || (targetType == null) || !(permission instanceof String)) {
            return false;
        }
        switch (targetType) {
            case "Participation":
            case "Repository":
                Participation participation = participationService.findOne((Long)targetId);
                return participation != null &&
                    (participation.getStudent().getLogin().equals(auth.getName())
                    || auth.getAuthorities().contains(adminAuthority)
                    || auth.getAuthorities().contains(taAuthority));
            default:
                return false;

        }


    }
}
