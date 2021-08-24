package de.tum.in.www1.artemis.web.rest;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.NotificationOption;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.NotificationOptionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.NotificationSettingsService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.PaginationUtil;
import io.swagger.annotations.ApiParam;

/**
 * REST controller for managing NotificationSettings (NotificationOptions).
 */
@RestController
@RequestMapping("/api")
public class NotificationSettingsResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final NotificationOptionRepository notificationOptionRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final NotificationSettingsService notificationSettingsService;

    public NotificationSettingsResource(NotificationOptionRepository notificationOptionRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, NotificationSettingsService notificationSettingsService) {
        this.notificationOptionRepository = notificationOptionRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * Fetches the NotificationOptions for the current user from the server.
     * These are only the options that the user has already modified.
     * NotificationOptions (Server) corresponds to NotificationOptionCores (Client)
     * @return the list of found NotificationOptions
     */
    @GetMapping("/notification-settings/fetch-options")
    public ResponseEntity<List<NotificationOption>> getNotificationOptionsForCurrentUser(@ApiParam Pageable pageable) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        final Page<NotificationOption> page = notificationOptionRepository.findAllNotificationOptionsForRecipientWithId(currentUser.getId(), pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * Saves the provided NotificationOptions to the server.
     * @param notificationOptions which should be saved to the notificationOption database.
     * @return all available UserOptions for the current user as array (including the newly saved ones)
     */
    @PostMapping("/notification-settings/save-options")
    public ResponseEntity<NotificationOption[]> saveUserOptionsForCurrentUser(@RequestBody NotificationOption[] notificationOptions) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        notificationSettingsService.setCurrentUser(notificationOptions, currentUser);
        List<NotificationOption> resultAsList = notificationOptionRepository.saveAll(Arrays.stream(notificationOptions).toList());
        NotificationOption[] resultAsArray = resultAsList.toArray(new NotificationOption[resultAsList.size()]);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "notificationoption", "test")).body(resultAsArray);
    }
}
