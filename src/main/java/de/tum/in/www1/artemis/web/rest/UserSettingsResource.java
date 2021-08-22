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

    /**
     * Fetches the UserOptions for the current user from the server.
     * These are only the options that the user has already modified.
     * UserOptions (Server) corresponds to OptionCores (Client)
     * @return the list of found UserOptions
     */
    @GetMapping("/user-settings/fetch-options")
    public ResponseEntity<List<UserOption>> getUserOptionsForCurrentUser(@ApiParam Pageable pageable) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        final Page<UserOption> page = userOptionRepository.findAllUserOptionsForRecipientWithId(currentUser.getId(), pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * Saves the provided UserOptions to the server.
     * @param options which should be saved to the userOption database.
     * @return all available UserOptions for the current user as array (including the newly saved ones)
     */
    @PostMapping("/user-settings/save-options")
    public ResponseEntity<UserOption[]> saveUserOptionsForCurrentUser(@RequestBody UserOption[] options) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        for (UserOption option : options) {
            option.setUser(currentUser);
        }
        List<UserOption> result = userOptionRepository.saveAll(Arrays.stream(options).toList());
        UserOption[] result2 = result.toArray(new UserOption[result.size()]);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "test")).body(result2);
    }
}
