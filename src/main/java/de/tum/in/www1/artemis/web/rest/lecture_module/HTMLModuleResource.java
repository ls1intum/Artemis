package de.tum.in.www1.artemis.web.rest.lecture_module;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.conflict;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lecture_module.HTMLModule;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.lecture_module.HTMLModuleService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class HTMLModuleResource {

    private final Logger log = LoggerFactory.getLogger(HTMLModuleResource.class);

    private static final String ENTITY_NAME = "htmlModule";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final AuthorizationCheckService authorizationCheckService;

    private final HTMLModuleService htmlModuleService;

    public HTMLModuleResource(UserService userService, AuthorizationCheckService authorizationCheckService, HTMLModuleService htmlModuleService) {
        this.userService = userService;
        this.authorizationCheckService = authorizationCheckService;
        this.htmlModuleService = htmlModuleService;
    }

    /**
     * POST /html-modules
     *
     * @param htmlModule the HTMLModule to create
     * @return the ResponseEntity with status 201 (created) and with the body of the new HTMLModule
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/html-modules")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<HTMLModule> saveHtmlModule(@RequestBody HTMLModule htmlModule) throws URISyntaxException {
        log.debug("REST request to save HTMLModule : {}", htmlModule);
        if (htmlModule.getId() != null) {
            throw new BadRequestAlertException("A new HTMLModule cannot already have an ID", ENTITY_NAME, "idexists");
        }
        if (htmlModule.getName() == null) {
            throw new BadRequestAlertException("A new textExercise needs a name", ENTITY_NAME, "missingname");
        }
        if (htmlModule.getLecture() == null) {
            return conflict();
        }
        if (htmlModule.getLecture().getCourse() == null) {
            return conflict();
        }
        Course course = htmlModule.getLecture().getCourse();

        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        HTMLModule persistedHtmlModule = htmlModuleService.saveHtmlModule(htmlModule);

        return ResponseEntity.created(new URI("/api/html-modules/" + persistedHtmlModule.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, persistedHtmlModule.getId().toString())).body(persistedHtmlModule);

    }

}
