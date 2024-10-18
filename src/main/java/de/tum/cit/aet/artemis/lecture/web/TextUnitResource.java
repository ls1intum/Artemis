package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class TextUnitResource {

    private static final Logger log = LoggerFactory.getLogger(TextUnitResource.class);

    private static final String ENTITY_NAME = "textUnit";

    private final LectureRepository lectureRepository;

    private final TextUnitRepository textUnitRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CompetencyProgressApi competencyProgressApi;

    public TextUnitResource(LectureRepository lectureRepository, TextUnitRepository textUnitRepository, AuthorizationCheckService authorizationCheckService,
            CompetencyProgressApi competencyProgressApi) {
        this.lectureRepository = lectureRepository;
        this.textUnitRepository = textUnitRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.competencyProgressApi = competencyProgressApi;
    }

    /**
     * GET lectures/:lectureId/text-units/:textUnitId : gets the text unit with the specified id
     *
     * @param textUnitId the id of the textUnit to retrieve
     * @param lectureId  the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the text unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/text-units/{textUnitId}")
    @EnforceAtLeastEditor
    public ResponseEntity<TextUnit> getTextUnit(@PathVariable Long textUnitId, @PathVariable Long lectureId) {
        log.debug("REST request to get TextUnit : {}", textUnitId);
        Optional<TextUnit> optionalTextUnit = textUnitRepository.findByIdWithCompetencies(textUnitId);
        if (optionalTextUnit.isEmpty()) {
            throw new EntityNotFoundException("TextUnit");
        }
        TextUnit textUnit = optionalTextUnit.get();
        if (textUnit.getLecture() == null || textUnit.getLecture().getCourse() == null || !textUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Input data not valid", ENTITY_NAME, "inputInvalid");
        }
        authorizationCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.EDITOR, textUnit.getLecture(), null);
        return ResponseEntity.ok().body(textUnit);
    }

    /**
     * PUT /lectures/:lectureId/text-units : Updates an existing text unit
     *
     * @param lectureId    the id of the lecture to which the text unit belongs to update
     * @param textUnitForm the text unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textUnit
     */
    @PutMapping("lectures/{lectureId}/text-units")
    @EnforceAtLeastEditor
    public ResponseEntity<TextUnit> updateTextUnit(@PathVariable Long lectureId, @RequestBody TextUnit textUnitForm) {
        log.debug("REST request to update an text unit : {}", textUnitForm);
        if (textUnitForm.getId() == null) {
            throw new BadRequestAlertException("A text unit must have an ID to be updated", ENTITY_NAME, "idNull");
        }

        var existingTextUnit = textUnitRepository.findByIdWithCompetencies(textUnitForm.getId()).orElseThrow();

        if (existingTextUnit.getLecture() == null || existingTextUnit.getLecture().getCourse() == null || !existingTextUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Input data not valid", ENTITY_NAME, "inputInvalid");
        }
        authorizationCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.EDITOR, existingTextUnit.getLecture(), null);

        textUnitForm.setId(existingTextUnit.getId());
        textUnitForm.setLecture(existingTextUnit.getLecture());
        TextUnit result = textUnitRepository.save(textUnitForm);

        competencyProgressApi.updateProgressForUpdatedLearningObjectAsync(existingTextUnit, Optional.of(textUnitForm));

        return ResponseEntity.ok(result);
    }

    /**
     * POST /lectures/:lectureId/text-units : creates a new text unit.
     *
     * @param lectureId the id of the lecture to which the text unit should be added
     * @param textUnit  the text unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new text unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("lectures/{lectureId}/text-units")
    @EnforceAtLeastEditor
    public ResponseEntity<TextUnit> createTextUnit(@PathVariable Long lectureId, @RequestBody TextUnit textUnit) throws URISyntaxException {
        log.debug("REST request to create TextUnit : {}", textUnit);
        if (textUnit.getId() != null) {
            throw new BadRequestAlertException("A new text unit cannot have an id", ENTITY_NAME, "idExists");
        }

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId);

        if (lecture.getCourse() == null || (textUnit.getLecture() != null && !lecture.getId().equals(textUnit.getLecture().getId()))) {
            throw new BadRequestAlertException("Input data not valid", ENTITY_NAME, "inputInvalid");
        }
        authorizationCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.EDITOR, lecture, null);

        // persist lecture unit before lecture to prevent "null index column for collection" error
        textUnit.setLecture(null);
        textUnit = textUnitRepository.saveAndFlush(textUnit);
        textUnit.setLecture(lecture);
        lecture.addLectureUnit(textUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        TextUnit persistedTextUnit = (TextUnit) updatedLecture.getLectureUnits().getLast();

        competencyProgressApi.updateProgressByLearningObjectAsync(persistedTextUnit);

        return ResponseEntity.created(new URI("/api/text-units/" + persistedTextUnit.getId())).body(persistedTextUnit);
    }
}
