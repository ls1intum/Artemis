package de.tum.in.www1.artemis.web.rest.settings.ide;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.settings.ide.Ide;
import de.tum.in.www1.artemis.domain.settings.ide.UserIdeMapping;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.settings.IdeRepository;
import de.tum.in.www1.artemis.repository.settings.UserIdeMappingRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;

/**
 * REST controller for managing NotificationSettings (NotificationSettings).
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class IdeSettingsResource {

    private static final Logger log = LoggerFactory.getLogger(IdeSettingsResource.class);

    private final UserIdeMappingRepository userIdeMappingRepository;

    private final IdeRepository ideRepository;

    private final UserRepository userRepository;

    public IdeSettingsResource(UserIdeMappingRepository userIdeMappingRepository, IdeRepository ideRepository, UserRepository userRepository) {
        this.userIdeMappingRepository = userIdeMappingRepository;
        this.ideRepository = ideRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET users/ides: get the predefined IDEs
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @GetMapping("ide-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<?> getIdesOfUser() {
        User user = userRepository.getUser();
        log.debug("REST request to get IDEs of user {}", user.getLogin());

        var ideMappings = userIdeMappingRepository.findAllByUserId(user.getId());
        var ideRecords = ideMappings.stream().map(x -> new IdeSettingsDTO(x.getProgrammingLanguage(), x.getIde()));
        log.debug("Successfully queried IDEs of user {}", user.getLogin());

        return ResponseEntity.ok(ideRecords);
    }

    /**
     * PUT users/ides: set the IDE for a programming Language of the user
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @PutMapping("ide-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<?> setIde(@RequestParam ProgrammingLanguage programmingLanguage, @RequestBody Ide ide) {
        User user = userRepository.getUser();
        log.debug("REST request to set IDE of user {}", user.getLogin());

        // find or create the ide
        ide = ideRepository.findByDeepLink(ide.getDeepLink()).orElse(ideRepository.save(ide));

        // Create or update user ide mapping
        UserIdeMapping ideMapping = new UserIdeMapping(user, programmingLanguage, ide);
        ideMapping = userIdeMappingRepository.save(ideMapping);
        var ideRecord = new IdeSettingsDTO(ideMapping.getProgrammingLanguage(), ideMapping.getIde());
        log.debug("Successfully set IDE of user {}", user.getLogin());

        return ResponseEntity.ok(ideRecord);
    }

    /**
     * DELETE users/ides: delete a programming language from a users ide preferences
     *
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found), or with status 400 (Bad Request)
     */
    @DeleteMapping("ide-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<?> deleteProgrammingLanguageOfUser(@RequestParam ProgrammingLanguage programmingLanguage) {
        User user = userRepository.getUser();
        log.debug("REST request to delete IDE of user {}", user.getLogin());

        userIdeMappingRepository.deleteByUserIdAndProgrammingLanguage(user.getId(), programmingLanguage);

        log.debug("Successfully deleted IDE of user {}", user.getLogin());
        return ResponseEntity.ok().build();
    }
}
