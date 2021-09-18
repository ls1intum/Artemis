package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.NotificationOption;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.NotificationOptionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.NotificationSettingsService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing NotificationSettings (NotificationOptions).
 */
@RestController
@RequestMapping("api/")
public class NotificationSettingsResource {

    private final Logger log = LoggerFactory.getLogger(NotificationSettingsResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final NotificationOptionRepository notificationOptionRepository;

    private final UserRepository userRepository;

    private final NotificationSettingsService notificationSettingsService;

    public NotificationSettingsResource(NotificationOptionRepository notificationOptionRepository, UserRepository userRepository,
            NotificationSettingsService notificationSettingsService) {
        this.notificationOptionRepository = notificationOptionRepository;
        this.userRepository = userRepository;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * GET notification-settings/fetch-options : Get all NotificationOptions for current user
     *
     * Fetches the NotificationOptions for the current user from the server.
     * These are only the options that the user has already modified.
     * NotificationOptions (Server) corresponds to NotificationOptionCores (Client)#
     *
     * @return the list of found NotificationOptions
     */
    @GetMapping("notification-settings/fetch-options")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<NotificationOption>> getNotificationOptionsForCurrentUser() {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get all NotificationOptions for current user {}", currentUser);
        final Set<NotificationOption> notificationOptionSet = notificationOptionRepository.findAllNotificationOptionsForRecipientWithId(currentUser.getId());
        return new ResponseEntity<>(notificationOptionSet, HttpStatus.OK);
    }

    /**
     * POST notification-settings/save-options : Save NotificationOptions for current user
     *
     * Saves the provided NotificationOptions to the server.
     * @param notificationOptions which should be saved to the notificationOption database.
     *
     * @return the UserOptions that just got saved for the current user as array
     * 200 for a successful execution, 400 if the user provided empty options to save, 500 if the save call returns empty options
     */
    @PostMapping("notification-settings/save-options")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<NotificationOption[]> saveNotificationOptionsForCurrentUser(@NotNull @RequestBody NotificationOption[] notificationOptions) {
        if (notificationOptions.length == 0) {
            return badRequest("notificationOptions", "400", "Can not save non existing Notification Options");
        }
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to save NotificationOptions : {} for current user {}", notificationOptions, currentUser);
        notificationSettingsService.setCurrentUser(notificationOptions, currentUser);
        List<NotificationOption> resultAsList = notificationOptionRepository.saveAll(Arrays.stream(notificationOptions).toList());
        if (resultAsList.isEmpty()) {
            return badRequest("notificationOptions", "500", "Error occurred during saving of Notification Options");
        }
        NotificationOption[] resultAsArray = resultAsList.toArray(new NotificationOption[0]);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "notificationOption", "test")).body(resultAsArray);
    }
}
