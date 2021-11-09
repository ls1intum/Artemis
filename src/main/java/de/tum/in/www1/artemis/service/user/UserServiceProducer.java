package de.tum.in.www1.artemis.service.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.dto.UserGroupDTO;

/**
 * Message producer to communicate with the user management microservice through message queue
 */
@Component
@EnableJms
public class UserServiceProducer {

    private final Logger log = LoggerFactory.getLogger(UserServiceProducer.class);

    private static final String USER_MANAGEMENT_CREATE_USER_GROUP_QUEUE = "user_management_create_user_group_queue";

    private static final String USER_MANAGEMENT_DELETE_USER_GROUP_QUEUE = "user_management_delete_user_group_queue";

    private static final String USER_MANAGEMENT_ADD_USER_TO_GROUP_QUEUE = "user_management_add_user_to_group_queue";

    private static final String USER_MANAGEMENT_JIRA_ADD_USER_TO_GROUP_QUEUE = "user_management_jira_add_user_to_group_queue";

    private static final String USER_MANAGEMENT_REMOVE_USER_FROM_GROUP_QUEUE = "user_management_remove_user_from_group_queue";

    private static final String USER_MANAGEMENT_GET_OR_CREATE_USER_QUEUE = "user_management_get_or_create_user_queue";

    private static final String USER_MANAGEMENT_IS_GROUP_AVAILABLE_QUEUE = "user_management_is_group_available_queue";

    @Autowired
    private final JmsTemplate jmsTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserServiceProducer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    /**
     * Require assigning the user a group
     *
     * @param user the user to be updated
     * @param groupName the name of the group to be assigned
     * @param role the role of the user
     */
    public void addUserToGroup(User user, String groupName, Role role) {
        UserGroupDTO data = new UserGroupDTO(user.getLogin(), groupName, role);
        log.info("Send data {}", data.toString());
        try {
            jmsTemplate.convertAndSend(USER_MANAGEMENT_ADD_USER_TO_GROUP_QUEUE, objectMapper.writeValueAsString(data));
        }
        catch (JsonProcessingException e) {
            log.error(e.toString());
        }
    }

    /**
     * Require deletion of a user group
     *
     * @param groupName the name of the group to be deleted
     */
    public void deleteUserGroup(String groupName) {
        UserGroupDTO data = new UserGroupDTO();
        data.setGroupName(groupName);
        log.info("Send data {}", data);
        try {
            jmsTemplate.convertAndSend(USER_MANAGEMENT_DELETE_USER_GROUP_QUEUE, objectMapper.writeValueAsString(data));
        }
        catch (JsonProcessingException e) {
            log.error(e.toString());
        }
    }

    /**
     * Require removal of a group from user details
     *
     * @param user the user to be updated
     * @param groupName the group name to be deleted
     * @param role the role of the user
     */
    public void removeUserFromGroup(User user, String groupName, Role role) {
        UserGroupDTO data = new UserGroupDTO(user.getLogin(), groupName, role);
        log.info("Send data {}", data);
        try {
            jmsTemplate.convertAndSend(USER_MANAGEMENT_REMOVE_USER_FROM_GROUP_QUEUE, objectMapper.writeValueAsString(data));
        }
        catch (JsonProcessingException e) {
            log.error(e.toString());
        }
    }
}
