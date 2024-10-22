package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import jakarta.ws.rs.BadRequestException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.net.InternetDomainName;

import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.dto.OnlineResourceDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.OnlineUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class OnlineUnitResource {

    private static final Logger log = LoggerFactory.getLogger(OnlineUnitResource.class);

    private static final String ENTITY_NAME = "onlineUnit";

    private final OnlineUnitRepository onlineUnitRepository;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CompetencyProgressService competencyProgressService;

    private final LectureUnitService lectureUnitService;

    public OnlineUnitResource(LectureRepository lectureRepository, AuthorizationCheckService authorizationCheckService, OnlineUnitRepository onlineUnitRepository,
            CompetencyProgressService competencyProgressService, LectureUnitService lectureUnitService) {
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.onlineUnitRepository = onlineUnitRepository;
        this.competencyProgressService = competencyProgressService;
        this.lectureUnitService = lectureUnitService;
    }

    /**
     * GET lectures/:lectureId/online-units/:onlineUnitId: gets the online unit with the specified id
     *
     * @param onlineUnitId the id of the onlineUnit to retrieve
     * @param lectureId    the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the online unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/online-units/{onlineUnitId}")
    @EnforceAtLeastEditor
    public ResponseEntity<OnlineUnit> getOnlineUnit(@PathVariable Long onlineUnitId, @PathVariable Long lectureId) {
        log.debug("REST request to get onlineUnit : {}", onlineUnitId);
        var onlineUnit = onlineUnitRepository.findByIdWithCompetenciesElseThrow(onlineUnitId);
        checkOnlineUnitCourseAndLecture(onlineUnit, lectureId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, onlineUnit.getLecture().getCourse(), null);
        return ResponseEntity.ok().body(onlineUnit);
    }

    /**
     * PUT /lectures/:lectureId/online-units : Updates an existing online unit .
     *
     * @param lectureId  the id of the lecture to which the online unit belongs to update
     * @param onlineUnit the online unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated onlineUnit
     */
    @PutMapping("lectures/{lectureId}/online-units")
    @EnforceAtLeastEditor
    public ResponseEntity<OnlineUnit> updateOnlineUnit(@PathVariable Long lectureId, @RequestBody OnlineUnit onlineUnit) {
        log.debug("REST request to update an online unit : {}", onlineUnit);
        if (onlineUnit.getId() == null) {
            throw new BadRequestException();
        }

        var existingOnlineUnit = onlineUnitRepository.findByIdWithCompetenciesElseThrow(onlineUnit.getId());

        checkOnlineUnitCourseAndLecture(existingOnlineUnit, lectureId);
        lectureUnitService.validateUrlStringAndReturnUrl(onlineUnit.getSource());

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, onlineUnit.getLecture().getCourse(), null);

        OnlineUnit result = lectureUnitService.saveWithCompetencyLinks(onlineUnit, onlineUnitRepository::save);

        competencyProgressService.updateProgressForUpdatedLearningObjectAsync(existingOnlineUnit, Optional.of(onlineUnit));

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
    @EnforceAtLeastEditor
    public ResponseEntity<OnlineUnit> createOnlineUnit(@PathVariable Long lectureId, @RequestBody final OnlineUnit onlineUnit) throws URISyntaxException {
        log.debug("REST request to create onlineUnit : {}", onlineUnit);
        if (onlineUnit.getId() != null) {
            throw new BadRequestException();
        }

        lectureUnitService.validateUrlStringAndReturnUrl(onlineUnit.getSource());

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new BadRequestAlertException("Specified lecture is not part of a course", ENTITY_NAME, "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // persist lecture unit before lecture to prevent "null index column for collection" error
        onlineUnit.setLecture(null);

        OnlineUnit persistedOnlineUnit = lectureUnitService.saveWithCompetencyLinks(onlineUnit, onlineUnitRepository::saveAndFlush);

        persistedOnlineUnit.setLecture(lecture);
        lecture.addLectureUnit(persistedOnlineUnit);
        lectureRepository.save(lecture);

        competencyProgressService.updateProgressByLearningObjectAsync(persistedOnlineUnit);

        return ResponseEntity.created(new URI("/api/online-units/" + persistedOnlineUnit.getId())).body(persistedOnlineUnit);
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

        if (!InternetDomainName.isValid(url.getHost()) || "localhost".equalsIgnoreCase(url.getHost())) {
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
