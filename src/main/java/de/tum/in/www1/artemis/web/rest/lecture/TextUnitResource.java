package de.tum.in.www1.artemis.web.rest.lecture;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.*;
import java.util.Optional;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class TextUnitResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(TextUnitResource.class);

    private static final String ENTITY_NAME = "textUnit";

    private final LectureRepository lectureRepository;

    private final TextUnitRepository textUnitRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public TextUnitResource(LectureRepository lectureRepository, TextUnitRepository textUnitRepository, AuthorizationCheckService authorizationCheckService) {
        this.lectureRepository = lectureRepository;
        this.textUnitRepository = textUnitRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET lectures/:lectureId/text-units/:textUnitId : gets the text unit with the specified id
     *
     * @param textUnitId the id of the textUnit to retrieve
     * @param lectureId the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the text unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/text-units/{textUnitId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<TextUnit> getTextUnit(@PathVariable Long textUnitId, @PathVariable Long lectureId) {
        log.debug("REST request to get TextUnit : {}", textUnitId);
        Optional<TextUnit> optionalTextUnit = textUnitRepository.findById(textUnitId);
        if (optionalTextUnit.isEmpty()) {
            return notFound();
        }
        TextUnit textUnit = optionalTextUnit.get();
        if (textUnit.getLecture() == null || textUnit.getLecture().getCourse() == null) {
            return conflict();
        }
        if (!textUnit.getLecture().getId().equals(lectureId)) {
            return conflict();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(textUnit.getLecture().getCourse(), null)) {
            return forbidden();
        }
        return ResponseEntity.ok().body(textUnit);
    }

    /**
     * PUT /lectures/:lectureId/text-units : Updates an existing text unit
     *
     * @param lectureId      the id of the lecture to which the text unit belongs to update
     * @param textUnit the text unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textUnit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/lectures/{lectureId}/text-units")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<TextUnit> updateTextUnit(@PathVariable Long lectureId, @RequestBody TextUnit textUnit) {
        log.debug("REST request to update an text unit : {}", textUnit);
        if (textUnit.getId() == null) {
            return badRequest();
        }

        if (textUnit.getLecture() == null || textUnit.getLecture().getCourse() == null) {
            return conflict();
        }
        if (!textUnit.getLecture().getId().equals(lectureId)) {
            return conflict();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(textUnit.getLecture().getCourse(), null)) {
            return forbidden();
        }

        TextUnit result = textUnitRepository.save(textUnit);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, textUnit.getId().toString())).body(result);
    }

    /**
     * POST /lectures/:lectureId/text-units : creates a new text unit.
     *
     * @param lectureId      the id of the lecture to which the text unit should be added
     * @param textUnit the text unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new text unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/lectures/{lectureId}/text-units")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<TextUnit> createTextUnit(@PathVariable Long lectureId, @RequestBody TextUnit textUnit) throws URISyntaxException {
        log.debug("REST request to create TextUnit : {}", textUnit);
        if (textUnit.getId() != null) {
            return badRequest();
        }
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(lectureId);
        if (lectureOptional.isEmpty()) {
            return badRequest();
        }
        Lecture lecture = lectureOptional.get();
        if (lecture.getCourse() == null) {
            return conflict();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(lecture.getCourse(), null)) {
            return forbidden();
        }

        // persist lecture unit before lecture to prevent "null index column for collection" error
        textUnit.setLecture(null);
        textUnit = textUnitRepository.saveAndFlush(textUnit);
        textUnit.setLecture(lecture);
        lecture.addLectureUnit(textUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        TextUnit persistedTextUnit = (TextUnit) updatedLecture.getLectureUnits().get(updatedLecture.getLectureUnits().size() - 1);

        return ResponseEntity.created(new URI("/api/text-units/" + persistedTextUnit.getId())).headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ""))
                .body(persistedTextUnit);

    }

}
