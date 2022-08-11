package de.tum.in.www1.artemis.web.rest.lecture;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.ws.rs.BadRequestException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import de.tum.in.www1.artemis.web.rest.dto.OnlineResourceDTO;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

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
        var onlineUnit = onlineUnitRepository.findByIdElseThrow(onlineUnitId);
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
    public ResponseEntity<OnlineUnit> updateOnlineUnit(@PathVariable Long lectureId, @RequestBody OnlineUnit onlineUnit) {
        log.debug("REST request to update an online unit : {}", onlineUnit);
        if (onlineUnit.getId() == null) {
            throw new BadRequestException();
        }

        if (onlineUnit.getLecture() == null || onlineUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "onlineUnit", "lectureOrCourseMissing");
        }

        validateUrl(onlineUnit);

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, onlineUnit.getLecture().getCourse(), null);

        if (!onlineUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "onlineUnit", "lectureIdMismatch");
        }

        OnlineUnit result = onlineUnitRepository.save(onlineUnit);
        return ResponseEntity.ok(result);
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
    public ResponseEntity<OnlineUnit> createOnlineUnit(@PathVariable Long lectureId, @RequestBody final OnlineUnit onlineUnit) throws URISyntaxException {
        log.debug("REST request to create onlineUnit : {}", onlineUnit);
        if (onlineUnit.getId() != null) {
            throw new BadRequestException();
        }

        validateUrl(onlineUnit);

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new ConflictException("Specified lecture is not part of a course", "onlineUnit", "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // persist lecture unit before lecture to prevent "null index column for collection" error
        onlineUnit.setLecture(null);
        OnlineUnit persistedOnlineUnit = onlineUnitRepository.saveAndFlush(onlineUnit);

        persistedOnlineUnit.setLecture(lecture);
        lecture.addLectureUnit(persistedOnlineUnit);
        lectureRepository.save(lecture);

        return ResponseEntity.created(new URI("/api/online-units/" + persistedOnlineUnit.getId())).body(persistedOnlineUnit);
    }

    /**
     * Fetch the website's metadata from the specified link to an online resource
     *
     * @param link The link (as request parameter) to the website to fetch the metadata from
     * @return A DTO with link, meta title, and meta description
     */
    @GetMapping("/lectures/online-units/fetch-online-resource")
    @PreAuthorize("hasRole('EDITOR')")
    public OnlineResourceDTO getOnlineResource(@RequestParam("link") String link) {
        try {
            // Ensure that the link is a correctly formed URL
            URL url = new URL(link);

            log.info("Requesting online resource at {}", url);

            // Request the document, limited to 3 seconds and 500 KB (enough for most websites)
            Document document = Jsoup.connect(url.toString()).timeout(3000).maxBodySize(500000).get();
            String title = getMetaTagContent(document, "title");
            String description = getMetaTagContent(document, "description");

            return new OnlineResourceDTO(url.toString(), title, description);
        }
        catch (MalformedURLException e) {
            throw new BadRequestException("The specified link is not a valid URL");
        }
        catch (IOException e) {
            throw new InternalServerErrorException("Error while retrieving metadata from link");
        }
    }

    /**
     * Returns the content of the specified meta tag
     * Inspired by https://www.javachinna.com/generate-rich-link-preview-for-a-given-url-based-on-the-meta-tags-present-in-the-web-page-in-spring-boot/
     *
     * @param document The Jsoup document to query
     * @param tag The meta tag from which to fetch the content
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
     * Validates the source url of an online unit.
     * @param onlineUnit The online unit to check the source URL for.
     */
    private void validateUrl(OnlineUnit onlineUnit) {
        try {
            new URL(onlineUnit.getSource());
        }
        catch (MalformedURLException exception) {
            throw new BadRequestException();
        }
    }
}
