package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;

@Profile(PROFILE_CORE)
@Service
public class FaqService {

    private static final String METIS_ANSWER_POST_ENTITY_NAME = "metis.answerPost";

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final FaqRepository faqRepository;

    public FaqService(FaqRepository faqRepository, Optional<PyrisWebhookService> pyrisWebhookService) {

        this.pyrisWebhookService = pyrisWebhookService;
        this.faqRepository = faqRepository;

    }

    public void ingestFaqsIntoPyris(Long courseId, Optional<Long> faqId) {
        if (!pyrisWebhookService.isPresent()) {
            return;
        }

        faqId.ifPresentOrElse(id -> {
            Faq faq = faqRepository.findById(id).orElseThrow();
            if (faq.getFaqState() != FaqState.ACCEPTED) {
                throw new IllegalArgumentException("Faq is not in the state accepted, you cannot ingest this faq");
            }
            pyrisWebhookService.get().addFaqToPyris(faq);
        }, () -> faqRepository.findAllByCourseIdAndFaqState(courseId, FaqState.ACCEPTED).forEach(faq -> pyrisWebhookService.get().addFaqToPyris(faq)));
    }

}
