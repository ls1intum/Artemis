package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastEditorInLecture;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastEditorInLectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/lecture/")
public class TextUnitResource {

    private static final Logger log = LoggerFactory.getLogger(TextUnitResource.class);

    private static final String ENTITY_NAME = "textUnit";

    private final LectureRepository lectureRepository;

    private final TextUnitRepository textUnitRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final LectureUnitService lectureUnitService;

    public TextUnitResource(LectureRepository lectureRepository, TextUnitRepository textUnitRepository, Optional<CompetencyProgressApi> competencyProgressApi,
            LectureUnitService lectureUnitService) {
        this.lectureRepository = lectureRepository;
        this.textUnitRepository = textUnitRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.lectureUnitService = lectureUnitService;
    }

    /**
     * GET lectures/:lectureId/text-units/:textUnitId : gets the text unit with the specified id
     *
     * @param textUnitId the id of the textUnit to retrieve
     * @param lectureId  the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the text unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/text-units/{textUnitId}")
    @EnforceAtLeastEditorInLectureUnit(resourceIdFieldName = "textUnitId")
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
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<TextUnit> updateTextUnit(@PathVariable Long lectureId, @RequestBody TextUnit textUnitForm) {
        log.debug("REST request to update an text unit : {}", textUnitForm);
        if (textUnitForm.getId() == null) {
            throw new BadRequestAlertException("A text unit must have an ID to be updated", ENTITY_NAME, "idNull");
        }

        var existingTextUnit = textUnitRepository.findByIdWithCompetencies(textUnitForm.getId()).orElseThrow();

        if (existingTextUnit.getLecture() == null || existingTextUnit.getLecture().getCourse() == null || !existingTextUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Input data not valid", ENTITY_NAME, "inputInvalid");
        }

        textUnitForm.setId(existingTextUnit.getId());
        textUnitForm.setLecture(existingTextUnit.getLecture());

        TextUnit result = lectureUnitService.saveWithCompetencyLinks(textUnitForm, textUnitRepository::save);

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(existingTextUnit, Optional.of(textUnitForm)));

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
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<TextUnit> createTextUnit(@PathVariable Long lectureId, @RequestBody TextUnit textUnit) throws URISyntaxException {
        log.debug("REST request to create TextUnit : {}", textUnit);
        if (textUnit.getId() != null) {
            throw new BadRequestAlertException("A new text unit cannot have an id", ENTITY_NAME, "idExists");
        }

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
        if (lecture.getCourse() == null || (textUnit.getLecture() != null && !lecture.getId().equals(textUnit.getLecture().getId()))) {
            throw new BadRequestAlertException("Input data not valid", ENTITY_NAME, "inputInvalid");
        }

        lecture.addLectureUnit(textUnit);
        Lecture updatedLecture = lectureRepository.saveAndFlush(lecture);
        TextUnit persistedUnit = (TextUnit) updatedLecture.getLectureUnits().getLast();
        // From now on, only use persistedUnit
        lectureUnitService.saveWithCompetencyLinks(persistedUnit, textUnitRepository::saveAndFlush);
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(persistedUnit));

        lectureUnitService.disconnectCompetencyLectureUnitLinks(persistedUnit);
        return ResponseEntity.created(new URI("/api/text-units/" + persistedUnit.getId())).body(persistedUnit);
    }
}
