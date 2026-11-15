package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class FaqService {

    private static final Logger log = LoggerFactory.getLogger(FaqService.class);

    private final Optional<PyrisFaqApi> pyrisFaqApi;

    private final FaqRepository faqRepository;

    public FaqService(FaqRepository faqRepository, Optional<PyrisFaqApi> pyrisFaqApi) {
        this.pyrisFaqApi = pyrisFaqApi;
        this.faqRepository = faqRepository;
    }

    /**
     * Remove a deleted FAQ from Iris without delaying the request. Only ids cross the async boundary, so this also works
     * after the local FAQ has been deleted. Reconciliation retries failures.
     *
     * @param faqId    the deleted FAQ
     * @param courseId its course
     */
    @Async("faqTaskExecutor")
    public void deleteFaqInPyris(long faqId, long courseId) {
        if (pyrisFaqApi.isEmpty()) {
            return;
        }
        try {
            pyrisFaqApi.get().deleteFaq(faqId, courseId);
        }
        catch (RuntimeException e) {
            log.warn("Could not delete FAQ {} of course {} from Iris; reconciliation will retry", faqId, courseId, e);
        }
    }

    /**
     * Synchronize a saved FAQ with Iris in the background. Read the current persisted state on the worker instead of passing
     * a managed entity between threads or sending an older edit that waited in the queue. An FAQ that is no longer accepted
     * is removed from Iris. Reconciliation retries failures.
     *
     * @param faqId the saved FAQ
     */
    @Async("faqTaskExecutor")
    public void autoIngestFaqIntoPyris(long faqId) {
        if (pyrisFaqApi.isEmpty()) {
            return;
        }
        try {
            faqRepository.findById(faqId).ifPresent(faq -> {
                if (faq.getFaqState() == FaqState.ACCEPTED) {
                    pyrisFaqApi.get().addFaq(faq);
                }
                else {
                    pyrisFaqApi.get().deleteFaq(faq.getId(), faq.getCourse().getId());
                }
            });
        }
        catch (RuntimeException e) {
            log.warn("Could not synchronize FAQ {} with Iris; reconciliation will retry", faqId, e);
        }
    }
}
