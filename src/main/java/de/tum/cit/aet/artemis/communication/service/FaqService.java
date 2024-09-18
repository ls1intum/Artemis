package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;

@Profile(PROFILE_CORE)
@Service
public class FaqService {

    private final FaqRepository faqRepository;

    public FaqService(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    /**
     * Deletes the given faq
     *
     * @param faqId the faqId of to be deleted faq
     */
    public void deleteById(long faqId) {
        faqRepository.deleteById(faqId);

    }

    public Faq save(Faq faq) {
        return faqRepository.save(faq);
    }

    public Set<String> findAllCategoriesByCourseId(Long courseId) {
        faqRepository.findAllCategoriesByCourseId(courseId);
    }
}
