package de.tum.in.www1.exerciseapp.service;

import org.springframework.security.core.Authentication;

/**
 * Created by Josias Montag on 30.09.16.
 */
public interface RemoteUserService {


    /**
     * Performs authentication with using the remote user service.
     * See: org.springframework.security.authentication.AuthenticationProvider
     *
     * @param authentication the authentication request object.
     * @return a fully authenticated object including credentials. May return null if the AuthenticationProvider is unable to support authentication of the passed Authentication object. In such a case, the next AuthenticationProvider that supports the presented Authentication class will be tried.
     */
    public Authentication authenticate(Authentication authentication);

    /**
     * Returns true if this AuthenticationProvider supports the indicated Authentication object.
     * See: org.springframework.security.authentication.AuthenticationProvider
     *
     * @param authentication
     * @return true if the implementation can more closely evaluate the Authentication class presented
     */
    public boolean supports(Class<?> authentication);

    /**
     * Create a new user on the remote user service. This is currently only used for the LTI automatic sign in feature.
     *
     * @param username username to create on the remote user service
     * @param password password in clear text
     * @param emailAddress mail address
     * @param displayName display name (full name)
     */
    public void createUser(String username, String password, String emailAddress, String displayName);

    /**
     * Add a user to a group on the remote user service. This is currently only used for the LTI automatic sign in feature.
     * Should not throw an error, if the user is already in the group.
     *
     * @param username username on the remote user service
     * @param group group name on the remote user service
     */
    public void addUserToGroup(String username, String group);


}
