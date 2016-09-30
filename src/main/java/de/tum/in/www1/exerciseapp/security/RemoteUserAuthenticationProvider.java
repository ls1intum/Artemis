package de.tum.in.www1.exerciseapp.security;

import de.tum.in.www1.exerciseapp.service.RemoteUserService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;


import javax.inject.Inject;


/**
 * Created by muenchdo on 08/06/16.
 */
@Component
@ComponentScan("de.tum.in.www1.exerciseapp.*")
public class RemoteUserAuthenticationProvider implements AuthenticationProvider {

    @Inject
    RemoteUserService remoteUserService;


    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        return  remoteUserService.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return remoteUserService.supports(authentication);
    }

}
