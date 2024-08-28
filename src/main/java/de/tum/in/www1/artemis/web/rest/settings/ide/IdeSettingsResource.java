package de.tum.in.www1.artemis.web.rest.settings.ide;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.List;

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
import de.tum.in.www1.artemis.web.rest.dto.settings.ide.IdeDTO;
import de.tum.in.www1.artemis.web.rest.dto.settings.ide.IdeMappingDTO;

/**
 * REST controller for managing Ide Settings (IdeSettings).
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
     * GET ide-settings/predefined: get the predefined IDEs
     *
     * @return the predefined ides as IdeDTO
     */
    @GetMapping("ide-settings/predefined")
    @EnforceAtLeastStudent
    public ResponseEntity<List<IdeDTO>> getPredefinedIdes() {
        log.debug("REST request to get predefined IDEs");

        List<IdeDTO> ideRecords = Arrays.stream(Ide.PREDEFINED_IDES).map(IdeDTO::new).toList();
        log.debug("Successfully queried predefined IDEs");

        return ResponseEntity.ok(ideRecords);
    }

    /**
     * GET ide-settings: get the IDEs preferences of the user
     *
     * @return the ide preferences of the user as IdeMappingDTO
     */
    @GetMapping("ide-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<List<IdeMappingDTO>> getIdesOfUser() {
        User user = userRepository.getUser();
        log.debug("REST request to get IDEs of user {}", user.getLogin());

        var ideMappings = userIdeMappingRepository.findAllByUserId(user.getId());
        List<IdeMappingDTO> ideRecords = ideMappings.stream().map(ideMapping -> new IdeMappingDTO(ideMapping.getProgrammingLanguage(), ideMapping.getIde())).toList();
        log.debug("Successfully queried IDEs of user {}", user.getLogin());

        return ResponseEntity.ok(ideRecords);
    }

    /**
     * PUT ide-settings: set the IDE for a programming Language of the user
     *
     * @param programmingLanguage the programming language for which the ide should be set
     * @param ide                 the ide to set
     * @return returns the changed ide preferences of the user as IdeMappingDTO
     */
    @PutMapping("ide-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<IdeMappingDTO> setIde(@RequestParam ProgrammingLanguage programmingLanguage, @RequestBody IdeDTO ide) {
        User user = userRepository.getUser();
        log.debug("REST request to set IDE of user {}", user.getLogin());
        if (ide == null || ide.deepLink() == null || ide.name() == null) {
            log.error("Invalid request body for IDE setting of user {}", user.getLogin());
            return ResponseEntity.badRequest().build();
        }
        if (!ide.deepLink().contains("://") || !ide.deepLink().contains("{cloneUrl}")) {
            log.error("Invalid deep link for IDE {} of user {}", ide.deepLink(), user.getLogin());
            return ResponseEntity.badRequest().build();
        }

        var oldIdeMapping = userIdeMappingRepository.findById(new UserIdeMapping.UserIdeMappingId(user.getId(), programmingLanguage)).orElse(null);

        // find or create the ide
        var savedIde = ideRepository.findByDeepLink(ide.deepLink()).orElse(null);
        if (savedIde == null) {
            savedIde = ideRepository.save(new Ide(ide.name(), ide.deepLink()));
        }

        // Create or update user ide mapping
        UserIdeMapping ideMapping = new UserIdeMapping(user, programmingLanguage, savedIde);
        ideMapping = userIdeMappingRepository.save(ideMapping);

        orphanRemoval(oldIdeMapping != null ? oldIdeMapping.getIde() : null);

        var ideRecord = new IdeMappingDTO(ideMapping.getProgrammingLanguage(), ideMapping.getIde());
        log.debug("Successfully set IDE of user {}", user.getLogin());

        return ResponseEntity.ok(ideRecord);
    }

    /**
     * DELETE ide-settings: delete a programming language from a users ide preferences
     *
     * @param programmingLanguage the programming language preference to delete
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request)
     */
    @DeleteMapping("ide-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteProgrammingLanguageOfUser(@RequestParam ProgrammingLanguage programmingLanguage) {
        User user = userRepository.getUser();
        log.debug("REST request to delete IDE of user {}", user.getLogin());

        var ideMapping = userIdeMappingRepository.findById(new UserIdeMapping.UserIdeMappingId(user.getId(), programmingLanguage)).orElse(null);
        if (ideMapping == null) {
            log.error("No IDE found for user {} with programming language {}", user.getLogin(), programmingLanguage);
            return ResponseEntity.notFound().build();
        }

        userIdeMappingRepository.deleteById(new UserIdeMapping.UserIdeMappingId(user.getId(), programmingLanguage));

        orphanRemoval(ideMapping.getIde());

        log.debug("Successfully deleted IDE of user {}", user.getLogin());
        return ResponseEntity.ok().build();
    }

    /**
     * Remove the ide entity if it is not used by any user
     *
     * @param ide the ide to check for orphan removal
     */
    private void orphanRemoval(Ide ide) {
        // should be handled by orphan removal annotation in IDE, but annotation is still buggy with many-to-one
        if (ide == null || Arrays.stream(Ide.PREDEFINED_IDES).anyMatch(predefinedIde -> predefinedIde.getDeepLink().equals(ide.getDeepLink()))) {
            return;
        }

        if (!userIdeMappingRepository.existsByIde(ide)) {
            ideRepository.delete(ide);
        }
    }
}
