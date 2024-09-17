package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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

}
