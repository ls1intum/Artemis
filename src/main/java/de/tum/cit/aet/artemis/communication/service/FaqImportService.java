package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * Service for importing FAQs from one course to another.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class FaqImportService {

    private static final Logger log = LoggerFactory.getLogger(FaqImportService.class);

    private final FaqRepository faqRepository;

    public FaqImportService(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
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
            Faq newFaq = new Faq();
            newFaq.setQuestionTitle(sourceFaq.getQuestionTitle());
            newFaq.setQuestionAnswer(sourceFaq.getQuestionAnswer());
            newFaq.setCategories(new HashSet<>(sourceFaq.getCategories()));
            newFaq.setFaqState(sourceFaq.getFaqState());
            newFaq.setCourse(targetCourse);

            importedFaqs.add(faqRepository.save(newFaq));
        }

        log.debug("Imported {} FAQs to course {}", importedFaqs.size(), targetCourse.getId());
        return importedFaqs;
    }
}
