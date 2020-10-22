package de.tum.in.www1.artemis.web.rest.lecture_unit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.lecture_unit.LectureUnit;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.lecture_unit.LectureUnitService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class LectureUnitResource {

    private final Logger log = LoggerFactory.getLogger(HTMLUnitResource.class);

    private static final String ENTITY_NAME = "lectureUnit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final AuthorizationCheckService authorizationCheckService;

    private final LectureUnitService lectureUnitService;

    public LectureUnitResource(UserService userService, AuthorizationCheckService authorizationCheckService, LectureUnitService lectureUnitService) {
        this.userService = userService;
        this.authorizationCheckService = authorizationCheckService;
        this.lectureUnitService = lectureUnitService;
    }

    @PutMapping("/lectures/{lectureId}/lecture-units-order")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<List<LectureUnit>> updateLectureUnitsOrder(@PathVariable Long lectureId, @RequestBody List<LectureUnit> orderedLectureUnits) {
        log.debug("REST request to update the order of lecture units of lecture: {}", lectureId);
        List<LectureUnit> persistedOrderedLectureUnits = lectureUnitService.updateLectureUnitsOrder(lectureId, orderedLectureUnits);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "lectureUnitOrder", lectureId.toString())).body(persistedOrderedLectureUnits);
    }
}
