package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class FaqService {

    private final Optional<PyrisFaqApi> pyrisFaqApi;

    private final FaqRepository faqRepository;

    public FaqService(FaqRepository faqRepository, Optional<PyrisFaqApi> pyrisFaqApi) {
        this.pyrisFaqApi = pyrisFaqApi;
        this.faqRepository = faqRepository;

    }

    /**
     * Ingests FAQs into the Pyris system. If a specific FAQ ID is provided, the method will attempt to add
     * that FAQ to Pyris. Otherwise, it will ingest all FAQs for the specified course that are in the "ACCEPTED" state.
     * If the PyrisWebhookService is unavailable, the method does nothing.
     *
     * @param courseId the ID of the course for which FAQs will be ingested
     * @param faqId    an optional ID of a specific FAQ to ingest; if not provided, all accepted FAQs for the course are processed
     * @throws IllegalArgumentException if a specific FAQ is provided but its state is not "ACCEPTED"
     */
    public void ingestFaqsIntoPyris(Long courseId, Optional<Long> faqId) {
        if (pyrisFaqApi.isEmpty()) {
            return;
        }

        faqId.ifPresentOrElse(id -> {
            Faq faq = faqRepository.findById(id).orElseThrow();
            if (faq.getFaqState() != FaqState.ACCEPTED) {
                throw new IllegalArgumentException("Faq is not in the state accepted, you cannot ingest this faq");
            }
            pyrisFaqApi.get().addFaq(faq);
        }, () -> faqRepository.findAllByCourseIdAndFaqStateOrderByCreatedDateDesc(courseId, FaqState.ACCEPTED).forEach(faq -> pyrisFaqApi.get().addFaq(faq)));
    }

    /**
     * Deletes an existing FAQ from the Pyris system. If the PyrisWebhookService is unavailable, the method does nothing.
     *
     * @param existingFaq the FAQ to be removed from Pyris
     */
    public void deleteFaqInPyris(Faq existingFaq) {
        if (pyrisFaqApi.isEmpty()) {
            return;
        }

        pyrisFaqApi.get().deleteFaq(existingFaq);
    }

    /**
     * Automatically updates or ingests a specific FAQ into the Pyris system for a given course.
     * If the PyrisWebhookService is unavailable, the method does nothing.
     *
     * @param faq the FAQ to be ingested or updated in Pyris
     */
    public void autoIngestFaqIntoPyris(Faq faq) {
        if (pyrisFaqApi.isEmpty()) {
            return;
        }

        if (faq.getFaqState() != FaqState.ACCEPTED) {
            return;
        }

        pyrisFaqApi.get().autoUpdateFaqInPyris(faq);
    }
}
