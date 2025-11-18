package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLearningObjectLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.dto.OnlineResourceDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastEditorInLecture;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastEditorInLectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.lecture.dto.OnlineUnitDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.OnlineUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/lecture/")
public class OnlineUnitResource {

    private static final Logger log = LoggerFactory.getLogger(OnlineUnitResource.class);

    private static final String ENTITY_NAME = "onlineUnit";

    private final OnlineUnitRepository onlineUnitRepository;

    private final LectureRepository lectureRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final LectureUnitService lectureUnitService;

    private final LectureUnitRepository lectureUnitRepository;

    public OnlineUnitResource(LectureRepository lectureRepository, OnlineUnitRepository onlineUnitRepository, Optional<CompetencyProgressApi> competencyProgressApi,
            LectureUnitService lectureUnitService, LectureUnitRepository lectureUnitRepository) {
        this.lectureRepository = lectureRepository;
        this.onlineUnitRepository = onlineUnitRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.lectureUnitService = lectureUnitService;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    /**
     * GET lectures/:lectureId/online-units/:onlineUnitId: gets the online unit with the specified id
     *
     * @param onlineUnitId the id of the onlineUnit to retrieve
     * @param lectureId    the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the online unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/online-units/{onlineUnitId}")
    @EnforceAtLeastEditorInLectureUnit(resourceIdFieldName = "onlineUnitId")
    public ResponseEntity<OnlineUnit> getOnlineUnit(@PathVariable Long onlineUnitId, @PathVariable Long lectureId) {
        log.debug("REST request to get onlineUnit : {}", onlineUnitId);
        var onlineUnit = onlineUnitRepository.findByIdWithCompetenciesElseThrow(onlineUnitId);
        checkOnlineUnitCourseAndLecture(onlineUnit, lectureId);
        return ResponseEntity.ok().body(onlineUnit);
    }

    /**
     * PUT /lectures/:lectureId/online-units : Updates an existing online unit .
     *
     * @param lectureId     the id of the lecture to which the online unit belongs to update
     * @param onlineUnitDto the online unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated onlineUnit
     */
    @PutMapping("lectures/{lectureId}/online-units")
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<OnlineUnitDTO> updateOnlineUnit(@PathVariable Long lectureId, @RequestBody OnlineUnitDTO onlineUnitDto) {
        log.debug("REST request to update an online unit : {}", onlineUnitDto);
        if (onlineUnitDto.id() == null) {
            throw new BadRequestException();
        }

        var existingOnlineUnit = onlineUnitRepository.findByIdWithCompetenciesElseThrow(onlineUnitDto.id());

        // Validation
        checkOnlineUnitCourseAndLecture(existingOnlineUnit, lectureId);
        lectureUnitService.validateUrlStringAndReturnUrl(onlineUnitDto.source());

        // Precompute original competency IDs for progress update below
        Set<Long> originalCompetencyIds = existingOnlineUnit.getCompetencyLinks().stream().map(CompetencyLearningObjectLink::getCompetency).map(CourseCompetency::getId)
                .collect(Collectors.toSet());

        // copy all attributes
        existingOnlineUnit.setDescription(onlineUnitDto.description());
        existingOnlineUnit.setSource(onlineUnitDto.source());
        existingOnlineUnit.setName(onlineUnitDto.name());
        existingOnlineUnit.setReleaseDate(onlineUnitDto.releaseDate());

        // This method computes the relevant changes for competency links and applies them to the existingTextUnit
        lectureUnitService.updateCompetencyLinks(onlineUnitDto, existingOnlineUnit);

        // Note: Competency links are persisted automatically (due to CascadeType.PERSIST)
        existingOnlineUnit = onlineUnitRepository.save(existingOnlineUnit);

        if (competencyProgressApi.isPresent()) {
            // NOTE: this can be a very expensive operation, depending on how many users have progress for this learning object
            competencyProgressApi.get().updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, existingOnlineUnit);
        }

        // convert into DTO
        var result = new OnlineUnitDTO(existingOnlineUnit.getId(), existingOnlineUnit.getName(), existingOnlineUnit.getReleaseDate(), existingOnlineUnit.getDescription(),
                existingOnlineUnit.getSource(), existingOnlineUnit.getCompetencyLinks().stream()
                        .map(link -> new CompetencyLinkDTO(new CompetencyDTO(link.getCompetency().getId()), link.getWeight())).collect(Collectors.toSet()));

        return ResponseEntity.ok(result);
    }

    /**
     * POST /lectures/:lectureId/online-units : creates a new online unit.
     *
     * @param lectureId  the id of the lecture to which the online unit should be added
     * @param onlineUnit the online unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new online unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("lectures/{lectureId}/online-units")
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<OnlineUnit> createOnlineUnit(@PathVariable Long lectureId, @RequestBody final OnlineUnit onlineUnit) throws URISyntaxException {
        log.debug("REST request to create onlineUnit : {}", onlineUnit);
        if (onlineUnit.getId() != null) {
            throw new BadRequestAlertException("A new online unit cannot have an id", ENTITY_NAME, "idExists");
        }

        lectureUnitService.validateUrlStringAndReturnUrl(onlineUnit.getSource());

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
        if (lecture.getCourse() == null || (onlineUnit.getLecture() != null && !lecture.getId().equals(onlineUnit.getLecture().getId()))) {
            throw new BadRequestAlertException("Input data not valid", ENTITY_NAME, "inputInvalid");
        }

        lectureUnitRepository.reconnectCompetencyLinks(onlineUnit);

        lecture.addLectureUnit(onlineUnit);
        Lecture updatedLecture = lectureRepository.saveAndFlush(lecture);

        OnlineUnit persistedUnit = (OnlineUnit) updatedLecture.getLectureUnits().getLast();
        // From now on, only use persistedUnit
        lectureUnitService.saveWithCompetencyLinks(persistedUnit, onlineUnitRepository::saveAndFlush);
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(persistedUnit));

        // TODO: return a DTO instead to avoid manipulation of the entity before sending it to the client
        lectureUnitService.disconnectCompetencyLectureUnitLinks(persistedUnit);
        return ResponseEntity.created(new URI("/api/online-units/" + persistedUnit.getId())).body(persistedUnit);
    }

    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^(?=.{1,253}$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*\\.[A-Za-z]{2,}$");

    private static boolean isValidDomain(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }

        // Reject localhost explicitly
        if ("localhost".equalsIgnoreCase(host)) {
            return false;
        }

        // Convert to ASCII (punycode) for IDN safety
        String asciiHost = IDN.toASCII(host);

        return DOMAIN_PATTERN.matcher(asciiHost).matches();
    }

    /**
     * GET /lectures/online-units/fetch-online-resource : Fetch the website's metadata from the specified link to an online resource.
     *
     * @param link The link (as request parameter) to the website to fetch the metadata from
     * @return the ResponseEntity with status 200 (OK) and with body a DTO with link, meta title, and meta description
     */
    @GetMapping("lectures/online-units/fetch-online-resource")
    @EnforceAtLeastEditor
    public ResponseEntity<OnlineResourceDTO> getOnlineResource(@RequestParam("link") String link) {
        // Ensure that the link is a correctly formed URL
        URL url = lectureUnitService.validateUrlStringAndReturnUrl(link);

        if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new BadRequestException("The specified link uses an unsupported protocol");
        }

        if (!isValidDomain(url.getHost())) {
            throw new BadRequestException("The specified link does not contain a valid domain");
        }

        log.info("Requesting online resource at {}", url);

        try {
            // Request the document, limited to 3 seconds and 500 KB (enough for most websites)
            Document document = Jsoup.connect(url.toString()).timeout(3000).maxBodySize(500000).get();
            String title = getMetaTagContent(document, "title");
            String description = getMetaTagContent(document, "description");

            return ResponseEntity.ok(new OnlineResourceDTO(url.toString(), title, description));
        }
        catch (IOException e) {
            throw new InternalServerErrorException("Error while retrieving metadata from link");
        }
    }

    /**
     * Returns the content of the specified meta tag
     * Inspired by <a href="https://www.javachinna.com/generate-rich-link-preview-for-a-given-url-based-on-the-meta-tags-present-in-the-web-page-in-spring-boot/">...</a>
     *
     * @param document The Jsoup document to query
     * @param tag      The meta tag from which to fetch the content
     * @return The value of the content attribute of the specified tag
     */
    private String getMetaTagContent(Document document, String tag) {
        Element element = document.select("meta[name=" + tag + "]").first();
        if (element != null && !element.attr("content").isBlank()) {
            return element.attr("content");
        }
        element = document.select("meta[property=og:" + tag + "]").first();
        if (element != null) {
            return element.attr("content");
        }
        return "";
    }

    /**
     * Checks that the online unit belongs to the specified lecture.
     *
     * @param onlineUnit The online unit to check
     * @param lectureId  The id of the lecture to check against
     */
    private void checkOnlineUnitCourseAndLecture(OnlineUnit onlineUnit, Long lectureId) {
        if (onlineUnit.getLecture() == null || onlineUnit.getLecture().getCourse() == null) {
            throw new BadRequestAlertException("Lecture unit must be associated to a lecture of a course", ENTITY_NAME, "lectureOrCourseMissing");
        }
        if (!onlineUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Requested lecture unit is not part of the specified lecture", ENTITY_NAME, "lectureIdMismatch");
        }
    }
}
