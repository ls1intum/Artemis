package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

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

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Faq;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.FaqRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.FaqService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

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

    private final FaqRepository faqRepository;

    private final FaqService faqService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public FaqResource(FaqRepository faqRepository, FaqService faqService, CourseRepository courseRepository, AuthorizationCheckService authCheckService) {

        this.faqRepository = faqRepository;
        this.faqService = faqService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * POST /faqs : Create a new faq.
     *
     * @param faq the faq to create
     * @return the ResponseEntity with status 201 (Created) and with body the new faq, or with status 400 (Bad Request) if the faq has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("faqs")
    @EnforceAtLeastEditor
    public ResponseEntity<Faq> createFaq(@RequestBody Faq faq) throws URISyntaxException {
        log.debug("REST request to save Faq : {}", faq);
        if (faq.getId() != null) {
            throw new BadRequestAlertException("A new faq cannot already have an ID", ENTITY_NAME, "idExists");
        }
        System.out.println("Test");
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, faq.getCourse(), null);

        Faq savedFaq = faqRepository.save(faq);
        return ResponseEntity.created(new URI("/api/faqs/" + savedFaq.getId())).body(savedFaq);
    }

    /**
     * PUT /faqs/{faqId} : Updates an existing faq.
     *
     * @param faq the faq to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated faq, or with status 400 (Bad Request) if the faq is not valid, or with status 500 (Internal
     *         Server Error) if the faq couldn't be updated
     */
    @PutMapping("faqs")
    @EnforceAtLeastEditor
    public ResponseEntity<Faq> updateFaq(@RequestBody Faq faq) {
        log.debug("REST request to update Faq : {}", faq);
        if (faq.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idNull");
        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, faq.getCourse(), null);
        Faq result = faqRepository.save(faq);
        return ResponseEntity.ok().body(result);
    }

    /**
     * GET /courses/:courseId/faqs : get all the faqs of a course
     *
     * @param courseId the courseId of the course for which all faqs should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of faqs in body
     */
    @GetMapping("courses/{courseId}/faqs")
    @EnforceAtLeastEditor
    public ResponseEntity<Set<Faq>> getFaqForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Faqs for the course with id : {}", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        Set<Faq> faqs = faqRepository.findAllByCourseId(courseId);

        return ResponseEntity.ok().body(faqs);
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
        Faq faq = faqRepository.findById(faqId).orElseThrow();

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
        faqService.delete(faqId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, faqId.toString())).build();
    }
}
