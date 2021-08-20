package de.tum.in.www1.artemis.web.rest;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.UserOption;
import de.tum.in.www1.artemis.repository.UserOptionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.PaginationUtil;
import io.swagger.annotations.ApiParam;

/**
 * REST controller for managing UserSettings.
 */
@RestController
@RequestMapping("/api")
public class UserSettingsResource {

    private final Logger log = LoggerFactory.getLogger(UserSettingsResource.class);

    private static final String ENTITY_NAME = "useroption";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserOptionRepository userOptionRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public UserSettingsResource(UserOptionRepository userOptionRepository, UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.userOptionRepository = userOptionRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    @GetMapping("/user-settings/fetch-options")
    public ResponseEntity<List<UserOption>> getNotificationOptionsForCurrentUser(@ApiParam Pageable pageable) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        final Page<UserOption> page = userOptionRepository.findAllUserOptionsForRecipientWithId(currentUser.getId(), pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /*
     * @PostMapping("/save-options") public ResponseEntity<NotificationOption[]> saveNotificationOptionsForCurrentUser(@RequestBody NotificationOption[] options) { // return
     * badRequest(); if (options == null) { return conflict(); } User currentUser = userRepository.getUserWithGroupsAndAuthorities(); Long currentUserId = currentUser.getId(); for
     * (NotificationOption option : options) { if (option.getUser_id().getId() != currentUserId) { // TODO try to rework User_id to actually be only the id return conflict(); } }
     * // TODO maybe more checks // NotificationOption[] savedOptions = notificationRepository.saveAllNotificationOptionsForRecipientWithId(currentUserId, options);
     * notificationRepository.saveAllNotificationOptionsForRecipientWithId(currentUserId, options); return ok(); }
     */

    // @PostMapping("/save-options") public ResponseEntity<UserOption[]> saveUserOptionsForCurrentUser(@RequestBody UserOption[] options) {
    /*
     * @PutMapping("/user-settings/save-options") public ResponseEntity<UserOption[]> saveUserOptionsForCurrentUser(@RequestBody UserOption[] options) { User currentUser =
     * userRepository.getUserWithGroupsAndAuthorities(); Long currentUserId = currentUser.getId(); for( UserOption option : options) { option.setUser_id(currentUser); }
     * List<UserOption> result = userOptionRepository.saveAll(Arrays.stream(options).toList()); //return
     * ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "test")).body(result); UserOption[] result2 = result.toArray(new
     * UserOption[result.size()]); return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "test")).body(result2); }
     */

    @PostMapping("/user-settings/save-options")
    public ResponseEntity<UserOption[]> saveUserOptionsForCurrentUser(@RequestBody UserOption[] options) {
        // return badRequest();
        /*
         * if (options == null) { return conflict(); }
         */
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        Long currentUserId = currentUser.getId();

        for (UserOption option : options) {
            option.setUser(currentUser);
        }

        /*
         * for (UserOption option : options) { if (option.getUser_id().getId() != currentUserId) { // TODO try to rework User_id to actually be only the id return conflict(); } }
         * return forbidden(); }
         */
        // TODO maybe more checks // NotificationOption[] savedOptions = notificationRepository.saveAllNotificationOptionsForRecipientWithId(currentUserId, options);
        // UserOption[] result = notificationRepository.saveAllUserOptionsForRecipientWithId(currentUserId, options);
        // UserOption[] result = //notificationRepository.saveAll(options);
        // return ok();
        // return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, notification.getId().toString())).body(result);
        // UserOption[] result = userOptionRepository.saveAll(options);

        List<UserOption> result = userOptionRepository.saveAll(Arrays.stream(options).toList());
        // return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "test")).body(result);
        UserOption[] result2 = result.toArray(new UserOption[result.size()]);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "test")).body(result2);
    }

    @PostMapping("/user-settings/save-new-options")
    public ResponseEntity<UserOption[]> saveNewUserOptionsForCurrentUser(@RequestBody UserOption[] options) {

        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        Long currentUserId = currentUser.getId();
        /*
         * for(UserOption option : options) { option.setUser_id(currentUser); userOptionRepository.save(option); //resultList.add(userOptionRepository.saveNewOption(option)); }
         */
        UserOption dummy = new UserOption();
        dummy.setId(27L);
        dummy.setCategory("Notifications");
        dummy.setGroup("Exercise Notifications");
        dummy.setDescription("Please start working.");
        dummy.setWebapp(true);
        dummy.setEmail(false);
        dummy.setUser(currentUser);

        UserOption dummy2 = new UserOption();
        /*
         * dummy.setId(27L); dummy.setCategory("Notifications"); dummy.setGroup("Exercise Notifications"); dummy.setDescription("Please start working."); dummy.setWebapp(true);
         * dummy.setEmail(false); dummy.setUser(currentUser);
         */

        userOptionRepository.save(dummy2);

        List<UserOption> result = userOptionRepository.saveAll(Arrays.stream(options).toList());
        // return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "test")).body(result);
        UserOption[] result2 = result.toArray(new UserOption[result.size()]);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "test")).body(result2);
        /*
         * //return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "test")).body(result); UserOption[] resultArray =
         * resultList.toArray(new UserOption[resultList.size()]); return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME,
         * "test")).body(resultArray);
         */
    }
}
