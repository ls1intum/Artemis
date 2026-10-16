package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.FaqImportSearchResultDTO;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

/**
 * Service for importing FAQs from one course to another.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class FaqImportService {

    private static final Logger log = LoggerFactory.getLogger(FaqImportService.class);

    private final FaqRepository faqRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public FaqImportService(FaqRepository faqRepository, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService) {
        this.faqRepository = faqRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Searches only FAQs that the current caller could read through the normal FAQ endpoints.
     *
     * @param targetCourseId the target course, excluded from results
     * @param searchTerm     the literal search string
     * @param page           the zero-based page index
     * @param user           the current caller
     * @return a page of up to ten FAQs with source course information
     */
    public FaqImportSearchResultDTO searchForImport(long targetCourseId, String searchTerm, int page, User user) {
        var pageable = PageRequest.of(page, 10, Sort.by("questionTitle").ascending().and(Sort.by("id").ascending()));
        var result = faqRepository.findForImport(targetCourseId, user.getId(), authorizationCheckService.isCurrentUserAdminAccessEnabled(), searchTerm.strip(), pageable);
        return new FaqImportSearchResultDTO(result.getContent(), result.getTotalPages(), result.getTotalElements());
    }

    /**
     * Validates the entire selection before copying anything, including source visibility at import time.
     *
     * @param targetCourseId the target course
     * @param faqIds         unique source FAQ IDs
     * @param user           the current caller
     * @return the persisted copies with new IDs
     */
    public List<Faq> importSelectedFaqs(long targetCourseId, Set<Long> faqIds, User user) {
        List<Faq> sources = faqRepository.findAllById(faqIds);
        if (sources.size() != faqIds.size()) {
            throw new BadRequestAlertException("At least one selected FAQ no longer exists", "faq", "invalidSelection");
        }
        Map<Long, Boolean> studentAccess = new HashMap<>();
        Map<Long, Boolean> staffAccess = new HashMap<>();
        for (Faq source : sources) {
            long sourceCourseId = source.getCourse().getId();
            if (sourceCourseId == targetCourseId) {
                throw new BadRequestAlertException("Cannot import a FAQ into its own course", "faq", "sameCourse");
            }
            boolean visible = source.getFaqState() == FaqState.ACCEPTED
                    ? studentAccess.computeIfAbsent(sourceCourseId, id -> authorizationCheckService.isAtLeastStudentInCourse(id, user))
                    : staffAccess.computeIfAbsent(sourceCourseId, id -> authorizationCheckService.isAtLeastTeachingAssistantInCourse(id, user));
            if (!visible) {
                throw new AccessForbiddenException("No access to a selected FAQ");
            }
        }
        Course targetCourse = courseRepository.findByIdElseThrow(targetCourseId);
        List<Faq> copies = sources.stream().map(source -> copyFaq(source, targetCourse)).toList();
        return faqRepository.saveAll(copies);
    }

    private Faq copyFaq(Faq source, Course targetCourse) {
        Faq copy = new Faq();
        copy.setQuestionTitle(source.getQuestionTitle());
        copy.setQuestionAnswer(source.getQuestionAnswer());
        copy.setCategories(new HashSet<>(source.getCategories()));
        copy.setFaqState(source.getFaqState());
        copy.setCourse(targetCourse);
        return copy;
    }

    /**
     * Import all FAQs from the source course to the target course.
     *
     * @param sourceCourseId the ID of the course to import from
     * @param targetCourse   the course to import to
     * @return the list of imported FAQs
     */
    public List<Faq> importFaqs(long sourceCourseId, Course targetCourse) {
        log.debug("Importing FAQs from course {} to course {}", sourceCourseId, targetCourse.getId());

        List<Faq> sourceFaqs = faqRepository.findAllByCourseIdOrderByCreatedDateDesc(sourceCourseId);
        List<Faq> importedFaqs = new ArrayList<>();

        for (Faq sourceFaq : sourceFaqs) {
            importedFaqs.add(faqRepository.save(copyFaq(sourceFaq, targetCourse)));
        }

        log.debug("Imported {} FAQs to course {}", importedFaqs.size(), targetCourse.getId());
        return importedFaqs;
    }
}
