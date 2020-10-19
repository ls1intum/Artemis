package de.tum.in.www1.artemis.web.rest.lecture_unit;

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
import de.tum.in.www1.artemis.domain.lecture_unit.HTMLUnit;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.lecture_unit.HTMLUnitService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class HTMLUnitResource {

    private final Logger log = LoggerFactory.getLogger(HTMLUnitResource.class);

    private static final String ENTITY_NAME = "htmlUnit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final AuthorizationCheckService authorizationCheckService;

    private final HTMLUnitService htmlUnitService;

    public HTMLUnitResource(UserService userService, AuthorizationCheckService authorizationCheckService, HTMLUnitService htmlUnitService) {
        this.userService = userService;
        this.authorizationCheckService = authorizationCheckService;
        this.htmlUnitService = htmlUnitService;
    }

    /**
     * POST /html-units
     *
     * @param htmlUnit the HTMLUnit to create
     * @return the ResponseEntity with status 201 (created) and with the body of the new HTMLUnit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/html-units")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<HTMLUnit> saveHTMLUnit(@RequestBody HTMLUnit htmlUnit) throws URISyntaxException {
        log.debug("REST request to save HTMLUnit : {}", htmlUnit);
        if (htmlUnit.getId() != null) {
            throw new BadRequestAlertException("A new HTMLUnit cannot already have an ID", ENTITY_NAME, "idexists");
        }
        if (htmlUnit.getName() == null) {
            throw new BadRequestAlertException("A new HTMLUnit needs a name", ENTITY_NAME, "missingname");
        }
        if (htmlUnit.getLecture() == null) {
            return conflict();
        }
        if (htmlUnit.getLecture().getId() == null) {
            return conflict();
        }
        if (htmlUnit.getLecture().getCourse() == null) {
            return conflict();
        }
        Course course = htmlUnit.getLecture().getCourse();

        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        HTMLUnit persistedHtmlUnit = htmlUnitService.saveHtmlUnit(htmlUnit);

        return ResponseEntity.created(new URI("/api/html-units/" + persistedHtmlUnit.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, persistedHtmlUnit.getId().toString())).body(persistedHtmlUnit);

    }

}
