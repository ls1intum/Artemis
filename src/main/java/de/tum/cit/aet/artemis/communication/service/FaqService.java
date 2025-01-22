package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;

@Profile(PROFILE_IRIS)
@Service
public class FaqService {

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final ProfileService profileService;

    private final FaqRepository faqRepository;

    public FaqService(FaqRepository faqRepository, Optional<PyrisWebhookService> pyrisWebhookService, ProfileService profileService) {

        this.pyrisWebhookService = pyrisWebhookService;
        this.faqRepository = faqRepository;
        this.profileService = profileService;

    }

    public void ingestFaqsIntoPyris(Long courseId, Optional<Long> faqId) {
        if (pyrisWebhookService.isEmpty()) {
            return;
        }

        faqId.ifPresentOrElse(id -> {
            Faq faq = faqRepository.findById(id).orElseThrow();
            if (faq.getFaqState() != FaqState.ACCEPTED) {
                throw new IllegalArgumentException("Faq is not in the state accepted, you cannot ingest this faq");
            }
            pyrisWebhookService.get().addFaq(faq);
        }, () -> faqRepository.findAllByCourseIdAndFaqState(courseId, FaqState.ACCEPTED).forEach(faq -> pyrisWebhookService.get().addFaq(faq)));
    }

    public void deleteFaqInPyris(Faq existingFaq) {
        if (pyrisWebhookService.isEmpty()) {
            return;
        }

        pyrisWebhookService.get().deleteFaq(existingFaq);
    }

    public void autoIngestFaqsIntoPyris(Long courseId, Faq faq) {
        if (pyrisWebhookService.isEmpty()) {
            return;
        }

        pyrisWebhookService.get().autoUpdateFaqInPyris(courseId, faq);
    }
}
