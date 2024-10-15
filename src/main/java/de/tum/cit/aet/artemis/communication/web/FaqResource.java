package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.FaqDTO;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
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

    private final AuthorizationCheckService authCheckService;

    private final FaqRepository faqRepository;

    public FaqResource(CourseRepository courseRepository, AuthorizationCheckService authCheckService, FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * POST /courses/:courseId/faqs : Create a new faq.
     *
     * @param faq      the faq to create *
     * @param courseId the id of the course the faq belongs to
     * @return the ResponseEntity with status 201 (Created) and with body the new faq, or with status 400 (Bad Request)
     *         if the faq has already an ID or if the faq course id does not match with the path variable
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/faqs")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<FaqDTO> createFaq(@RequestBody Faq faq, @PathVariable Long courseId) throws URISyntaxException {
        log.debug("REST request to save Faq : {}", faq);
        if (faq.getId() != null) {
            throw new BadRequestAlertException("A new faq cannot already have an ID", ENTITY_NAME, "idExists");
        }
        isAtLeastInstructor(faq, courseId);
        if (faq.getCourse() == null || !faq.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("Course ID in path and FAQ do not match", ENTITY_NAME, "courseIdMismatch");
        }
        Faq savedFaq = faqRepository.save(faq);
        FaqDTO dto = new FaqDTO(savedFaq);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/faqs/" + savedFaq.getId())).body(dto);
    }

    /**
     * PUT /courses/:courseId/faqs/:faqId : Updates an existing faq.
     *
     * @param faq      the faq to update
     * @param faqId    id of the faq to be updated *
     * @param courseId the id of the course the faq belongs to
     * @return the ResponseEntity with status 200 (OK) and with body the updated faq, or with status 400 (Bad Request)
     *         if the faq is not valid or if the faq course id does not match with the path variable
     */
    @PutMapping("courses/{courseId}/faqs/{faqId}")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<FaqDTO> updateFaq(@RequestBody Faq faq, @PathVariable Long faqId, @PathVariable Long courseId) {
        log.debug("REST request to update Faq : {}", faq);
        if (faqId == null || !faqId.equals(faq.getId())) {
            throw new BadRequestAlertException("Id of FAQ and path must match", ENTITY_NAME, "idNull");
        }
        isAtLeastInstructor(faq, courseId);
        if (faq.getFaqState() == FaqState.ACCEPTED) {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        }

        Faq existingFaq = faqRepository.findByIdElseThrow(faqId);
        if (!Objects.equals(existingFaq.getCourse().getId(), courseId)) {
            throw new BadRequestAlertException("Course ID of the FAQ provided courseID must match", ENTITY_NAME, "idNull");
        }
        Faq updatedFaq = faqRepository.save(faq);
        FaqDTO dto = new FaqDTO(updatedFaq);
        return ResponseEntity.ok().body(dto);
    }

    /**
     * @param faq      the faq to be checked *
     * @param courseId the id of the course the faq belongs to
     *                     This method throws an expecption if a non-instructor in the course tries to set the state of an FAQ to ACCEPTED
     */
    private void isAtLeastInstructor(Faq faq, Long courseId) {
        if (faq.getFaqState() == FaqState.ACCEPTED) {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        }
    }

    /**
     * GET /courses/:courseId/faqs/:faqId : get the faq with the id faqId.
     *
     * @param faqId    the faqId of the faq to retrieve *
     * @param courseId the id of the course the faq belongs to
     * @return the ResponseEntity with status 200 (OK) and with body the faq, or with status 404 (Not Found)
     */
    @GetMapping("courses/{courseId}/faqs/{faqId}")
    @EnforceAtLeastStudent
    public ResponseEntity<FaqDTO> getFaq(@PathVariable Long faqId, @PathVariable Long courseId) {
        log.debug("REST request to get faq {}", faqId);
        Faq faq = faqRepository.findByIdElseThrow(faqId);
        if (faq.getCourse() == null || !faq.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("Course ID in path and FAQ do not match", ENTITY_NAME, "courseIdMismatch");
        }
        FaqDTO dto = new FaqDTO(faq);
        return ResponseEntity.ok(dto);
    }

    /**
     * DELETE /courses/:courseId/faqs/:faqId : delete the "id" faq.
     *
     * @param faqId    the id of the faq to delete
     * @param courseId the id of the course the faq belongs to
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/faqs/{faqId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deleteFaq(@PathVariable Long faqId, @PathVariable Long courseId) {

        log.debug("REST request to delete faq {}", faqId);
        Faq existingFaq = faqRepository.findByIdElseThrow(faqId);
        if (!Objects.equals(existingFaq.getCourse().getId(), courseId)) {
            throw new BadRequestAlertException("Course ID of the FAQ provided courseID must match", ENTITY_NAME, "idNull");
        }
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
    public ResponseEntity<Set<FaqDTO>> getFaqForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Faqs for the course with id : {}", courseId);
        Set<Faq> faqs = faqRepository.findAllByCourseId(courseId);
        Set<FaqDTO> faqDTOS = faqs.stream().map(FaqDTO::new).collect(Collectors.toSet());
        return ResponseEntity.ok().body(faqDTOS);
    }

    /**
     * GET /courses/:courseId/faq-status/:faqState : get all the faqs of a course in the specified status
     *
     * @param courseId the courseId of the course for which all faqs should be returned
     * @param faqState the state of all returned FAQs
     * @return the ResponseEntity with status 200 (OK) and the list of faqs in body
     */
    @GetMapping("courses/{courseId}/faq-state/{faqState}")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<FaqDTO>> getAllFaqForCourseByStatus(@PathVariable Long courseId, @PathVariable String faqState) {
        log.debug("REST request to get all Faqs for the course with id : " + courseId + "and status " + faqState, courseId);
        FaqState retrievedState = defineState(faqState);
        Set<Faq> faqs = faqRepository.findAllByCourseIdAndFaqState(courseId, retrievedState);
        Set<FaqDTO> faqDTOS = faqs.stream().map(FaqDTO::new).collect(Collectors.toSet());
        return ResponseEntity.ok().body(faqDTOS);
    }

    private FaqState defineState(String faqState) {
        return switch (faqState) {
            case "ACCEPTED" -> FaqState.ACCEPTED;
            case "REJECTED" -> FaqState.REJECTED;
            case "PROPOSED" -> FaqState.PROPOSED;
            default -> throw new IllegalArgumentException("Unknown state: " + faqState);
        };
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
        Set<String> faqs = faqRepository.findAllCategoriesByCourseId(courseId);

        return ResponseEntity.ok().body(faqs);
    }
}
