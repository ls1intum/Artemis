package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;

/**
 * REST controller for managing Faqs.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class FaqResource {

    private static final Logger log = LoggerFactory.getLogger(FaqResource.class);

    private static final String ENTITY_NAME = "faq";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CourseRepository courseRepository;

    private final FaqRepository faqRepository;

    private final AuthorizationCheckService authCheckService;

    public FaqResource(CourseRepository courseRepository, AuthorizationCheckService authCheckService, FaqRepository faqRepository) {

        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.faqRepository = faqRepository;
    }

    /**
     * POST /faqs : Create a new faq.
     *
     * @param faq the faq to create
     * @return the ResponseEntity with status 201 (Created) and with body the new faq, or with status 400 (Bad Request) if the faq has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("faqs")
    @EnforceAtLeastInstructor
    public ResponseEntity<Faq> createFaq(@RequestBody Faq faq) throws URISyntaxException {
        log.debug("REST request to save Faq : {}", faq);
        if (faq.getId() != null) {
            throw new BadRequestAlertException("A new faq cannot already have an ID", ENTITY_NAME, "idExists");
        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, faq.getCourse(), null);

        Faq savedFaq = faqRepository.save(faq);
        return ResponseEntity.created(new URI("/api/faqs/" + savedFaq.getId())).body(savedFaq);
    }

    /**
     * PUT /faqs/{faqId} : Updates an existing faq.
     *
     * @param faq   the faq to update
     * @param faqId id of the faq to be updated
     * @return the ResponseEntity with status 200 (OK) and with body the updated faq, or with status 400 (Bad Request) if the faq is not valid, or with status 500 (Internal
     *         Server Error) if the faq couldn't be updated
     */
    @PutMapping("faqs/{faqId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Faq> updateFaq(@RequestBody Faq faq, @PathVariable Long faqId) {
        log.debug("REST request to update Faq : {}", faq);
        if (faqId == null || !faqId.equals(faq.getId())) {
            throw new BadRequestAlertException("Id of FAQ and path must match", ENTITY_NAME, "idNull");
        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, faq.getCourse(), null);
        Faq existingFaq = faqRepository.findByIdElseThrow(faqId);
        Faq result = faqRepository.save(faq);
        return ResponseEntity.ok().body(result);
    }

    /**
     * GET /faqs/:faqId : get the "faqId" faq.
     *
     * @param faqId the faqId of the faq to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the faq, or with status 404 (Not Found)
     */
    @GetMapping("faqs/{faqId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Faq> getFaq(@PathVariable Long faqId) {
        log.debug("REST request to get faq {}", faqId);
        Faq faq = faqRepository.findByIdElseThrow(faqId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, faq.getCourse(), null);
        return ResponseEntity.ok(faq);
    }

    /**
     * DELETE /faqs/:faqId : delete the "id" faq.
     *
     * @param faqId the id of the faq to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("faqs/{faqId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteFaq(@PathVariable Long faqId) {

        log.debug("REST request to delete faq {}", faqId);
        Faq faq = faqRepository.findByIdElseThrow(faqId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, faq.getCourse(), null);
        faqRepository.deleteById(faqId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, faqId.toString())).build();
    }

    /**
     * GET /courses/:courseId/faqs : get all the faqs of a course
     *
     * @param courseId the courseId of the course for which all faqs should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of faqs in body
     */
    @GetMapping("courses/{courseId}/faqs")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<Faq>> getFaqForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Faqs for the course with id : {}", courseId);

        Course course = getCourseForRequest(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        Set<Faq> faqs = faqRepository.findAllByCourseId(courseId);
        return ResponseEntity.ok().body(faqs);
    }

    /**
     * GET /courses/:courseId/faq-categories : get all the faq categories of a course
     *
     * @param courseId the courseId of the course for which all faq categories should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of faqs in body
     */
    @GetMapping("courses/{courseId}/faq-categories")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<String>> getFaqCategoriesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Faq Categories for the course with id : {}", courseId);

        Course course = getCourseForRequest(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        Set<String> faqs = faqRepository.findAllCategoriesByCourseId(courseId);

        return ResponseEntity.ok().body(faqs);
    }

    /**
     *
     * @param courseId the courseId of the course
     * @return the course with the id courseId, unless it exists
     */
    private Course getCourseForRequest(Long courseId) {
        return courseRepository.findByIdElseThrow(courseId);
    }

}
