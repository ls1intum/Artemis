package de.tum.in.www1.artemis.web.rest.lecture;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.OnlineUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.OnlineUnitRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class OnlineUnitResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(OnlineUnitResource.class);

    private static final String ENTITY_NAME = "onlineUnit";

    private final OnlineUnitRepository onlineUnitRepository;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public OnlineUnitResource(LectureRepository lectureRepository, AuthorizationCheckService authorizationCheckService, OnlineUnitRepository onlineUnitRepository) {
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.onlineUnitRepository = onlineUnitRepository;
    }

    /**
     * GET lectures/:lectureId/online-units/:onlineUnitId: gets the online unit with the specified id
     *
     * @param onlineUnitId the id of the onlineUnit to retrieve
     * @param lectureId the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the online unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/online-units/{onlineUnitId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<OnlineUnit> getOnlineUnit(@PathVariable Long onlineUnitId, @PathVariable Long lectureId) {
        log.debug("REST request to get onlineUnit : {}", onlineUnitId);
        var onlineUnit = onlineUnitRepository.findById(onlineUnitId).orElseThrow(() -> new EntityNotFoundException("onlineUnit", onlineUnitId));
        if (onlineUnit.getLecture() == null || onlineUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "onlineUnit", "lectureOrCourseMissing");
        }
        if (!onlineUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "onlineUnit", "lectureIdMismatch");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, onlineUnit.getLecture().getCourse(), null);
        return ResponseEntity.ok().body(onlineUnit);
    }

    /**
     * PUT /lectures/:lectureId/online-units : Updates an existing online unit .
     *
     * @param lectureId      the id of the lecture to which the online unit belongs to update
     * @param onlineUnit the online unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated onlineUnit
     */
    @PutMapping("/lectures/{lectureId}/online-units")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<OnlineUnit> updateonlineUnit(@PathVariable Long lectureId, @RequestBody OnlineUnit onlineUnit) {
        log.debug("REST request to update an online unit : {}", onlineUnit);
        if (onlineUnit.getId() == null) {
            throw new BadRequestException();
        }

        if (onlineUnit.getLecture() == null || onlineUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "onlineUnit", "lectureOrCourseMissing");
        }

        // Validate the URL
        try {
            new URL(onlineUnit.getSource());
        }
        catch (MalformedURLException exception) {
            throw new BadRequestException();
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, onlineUnit.getLecture().getCourse(), null);

        if (!onlineUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "onlineUnit", "lectureIdMismatch");
        }

        OnlineUnit result = onlineUnitRepository.save(onlineUnit);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, onlineUnit.getId().toString())).body(result);
    }

    /**
     * POST /lectures/:lectureId/online-units : creates a new online unit.
     *
     * @param lectureId      the id of the lecture to which the online unit should be added
     * @param onlineUnit the online unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new online unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/lectures/{lectureId}/online-units")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<OnlineUnit> createonlineUnit(@PathVariable Long lectureId, @RequestBody OnlineUnit onlineUnit) throws URISyntaxException {
        log.debug("REST request to create onlineUnit : {}", onlineUnit);
        if (onlineUnit.getId() != null) {
            throw new BadRequestException();
        }

        // Validate the URL
        try {
            new URL(onlineUnit.getSource());
        }
        catch (MalformedURLException exception) {
            throw new BadRequestException();
        }

        Lecture lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new ConflictException("Specified lecture is not part of a course", "onlineUnit", "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // persist lecture unit before lecture to prevent "null index column for collection" error
        onlineUnit.setLecture(null);
        onlineUnit = onlineUnitRepository.saveAndFlush(onlineUnit);
        onlineUnit.setLecture(lecture);
        lecture.addLectureUnit(onlineUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        OnlineUnit persistedonlineUnit = (OnlineUnit) updatedLecture.getLectureUnits().get(updatedLecture.getLectureUnits().size() - 1);

        return ResponseEntity.created(new URI("/api/online-units/" + persistedonlineUnit.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(persistedonlineUnit);
    }

}
