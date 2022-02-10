package de.tum.in.www1.artemis.usermanagement.util;

import java.util.Set;

import de.tum.in.www1.artemis.domain.User;

public interface MockDelegate {

    void mockUpdateUserInUserManagement(String oldLogin, User user, Set<String> oldGroups) throws Exception;

    void mockCreateUserInUserManagement(User user, boolean userExistsInCi) throws Exception;

    void mockFailToCreateUserInExernalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) throws Exception;

    void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) throws Exception;
}
