package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.User;

public interface CIUserManagementService {

    void createUser(User user);

    void deleteUser(String userLogin);

    void updateUser(User user);
}
