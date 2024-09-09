package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.FaqRepository;

@Profile(PROFILE_CORE)
@Service
public class FaqService {

    private final FaqRepository faqRepository;

    public FaqService(FaqRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    /**
     * Deletes the given lecture (with its lecture units).
     *
     * @param faqId the faqId of to be deleted faq
     */
    public void deleteById(long faqId) {
        faqRepository.deleteById(faqId);

    }

}
