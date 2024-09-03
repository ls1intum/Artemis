package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile(PROFILE_CORE)
@Service
public class FaqService {

    public FaqService() {

    }

    /**
     * Deletes the given lecture (with its lecture units).
     *
     * @param faqId the faqId of to be deleted faq
     */
    public void delete(long faqId) {
    }

}
