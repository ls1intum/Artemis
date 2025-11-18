package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastEditorInLecture;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastEditorInLectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
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

    private final LectureUnitRepository lectureUnitRepository;

    private final CompetencyRepository competencyRepository;

    public TextUnitResource(LectureRepository lectureRepository, TextUnitRepository textUnitRepository, Optional<CompetencyProgressApi> competencyProgressApi,
            LectureUnitService lectureUnitService, LectureUnitRepository lectureUnitRepository, CompetencyRepository competencyRepository) {
        this.lectureRepository = lectureRepository;
        this.textUnitRepository = textUnitRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.lectureUnitService = lectureUnitService;
        this.lectureUnitRepository = lectureUnitRepository;
        this.competencyRepository = competencyRepository;
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
     * @param lectureId   the id of the lecture to which the text unit belongs to update
     * @param textUnitDto the text unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textUnit
     */
    @PutMapping("lectures/{lectureId}/text-units")
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<TextUnitDTO> updateTextUnit(@PathVariable Long lectureId, @RequestBody TextUnitDTO textUnitDto) {
        log.debug("REST request to update an text unit : {}", textUnitDto);
        if (textUnitDto.id() == null) {
            throw new BadRequestAlertException("A text unit must have an ID to be updated", ENTITY_NAME, "idNull");
        }

        var existingTextUnit = textUnitRepository.findByIdWithCompetencies(textUnitDto.id()).orElseThrow();

        if (existingTextUnit.getLecture() == null || existingTextUnit.getLecture().getCourse() == null || !existingTextUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Input data not valid", ENTITY_NAME, "inputInvalid");
        }

        // copy all attributes
        existingTextUnit.setContent(textUnitDto.content());
        existingTextUnit.setName(textUnitDto.name());
        existingTextUnit.setReleaseDate(textUnitDto.releaseDate());

        updateCompetencyLinks(textUnitDto, existingTextUnit);

        // Note: Competency links are persisted automatically (it should be done because of CascadeType.PERSIST)
        existingTextUnit = textUnitRepository.save(existingTextUnit);

        // TODO: reactivate
        // competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(existingTextUnit, Optional.of(textUnit)));

        // convert into DTO
        var result = new TextUnitDTO(existingTextUnit.getId(), existingTextUnit.getName(), existingTextUnit.getReleaseDate(), existingTextUnit.getContent(), existingTextUnit
                .getCompetencyLinks().stream().map(link -> new CompetencyLinkDTO(new CompetencyDTO(link.getCompetency().getId()), link.getWeight())).collect(Collectors.toSet()));
        return ResponseEntity.ok(result);
    }

    /**
     * Update the competency links of an existing text unit based on the provided DTO.
     * Supports removing links, updating weights of existing ones, and adding new links.
     *
     * @param lectureUnitDto      the DTO (from the client) containing the new state of competency links
     * @param existingLectureUnit the existing DB entity to update
     */
    private void updateCompetencyLinks(LectureUnitDTO lectureUnitDto, LectureUnit existingLectureUnit) {
        // TODO: only so that if the competency API is active
        // TODO: think about optimizing this by loading all new competencies in a single query
        if (lectureUnitDto.competencyLinks() == null || lectureUnitDto.competencyLinks().isEmpty()) {
            // this handles the case where all competency links were removed
            existingLectureUnit.getCompetencyLinks().clear();
        }
        else {
            // 1) Existing links indexed by competency id
            Map<Long, CompetencyLectureUnitLink> existingLinksByCompetencyId = existingLectureUnit.getCompetencyLinks().stream()
                    .collect(Collectors.toMap(link -> link.getCompetency().getId(), Function.identity()));

            // 2) New state of links (reusing existing ones where possible)
            Set<CompetencyLectureUnitLink> updatedLinks = new HashSet<>();

            for (var dtoLink : lectureUnitDto.competencyLinks()) {
                long competencyId = dtoLink.competency().id();
                double weight = dtoLink.weight();

                var existingLink = existingLinksByCompetencyId.get(competencyId);
                if (existingLink != null) {
                    // reuse managed entity, just update the weight
                    existingLink.setWeight(weight);
                    updatedLinks.add(existingLink);
                }
                else {
                    // no existing link → create a new one
                    var competency = competencyRepository.findByIdElseThrow(competencyId);
                    var newLink = new CompetencyLectureUnitLink(competency, existingLectureUnit, weight);

                    updatedLinks.add(newLink);
                }
            }

            // 3) Replace the contents of the managed collection, NOT the collection itself
            var managedSet = existingLectureUnit.getCompetencyLinks();
            managedSet.clear();
            managedSet.addAll(updatedLinks);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TextUnitDTO(Long id, String name, ZonedDateTime releaseDate, String content, Set<CompetencyLinkDTO> competencyLinks) implements LectureUnitDTO {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CompetencyLinkDTO(CompetencyDTO competency, double weight) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompetencyDTO(long id) {
    }

    public interface LectureUnitDTO {

        Long id();

        ZonedDateTime releaseDate();

        Set<CompetencyLinkDTO> competencyLinks();
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

        lectureUnitRepository.reconnectCompetencyLinks(textUnit);

        lecture.addLectureUnit(textUnit);
        Lecture updatedLecture = lectureRepository.saveAndFlush(lecture);
        TextUnit persistedUnit = (TextUnit) updatedLecture.getLectureUnits().getLast();
        // From now on, only use persistedUnit
        lectureUnitService.saveWithCompetencyLinks(persistedUnit, textUnitRepository::saveAndFlush);
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(persistedUnit));

        // TODO: return a DTO instead to avoid manipulation of the entity before sending it to the client
        lectureUnitService.disconnectCompetencyLectureUnitLinks(persistedUnit);
        return ResponseEntity.created(new URI("/api/text-units/" + persistedUnit.getId())).body(persistedUnit);
    }
}
