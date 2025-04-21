package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;

@Profile(PROFILE_IRIS)
@Controller
public class PyrisFaqApi extends AbstractIrisApi {

    private final PyrisWebhookService pyrisWebhookService;

    public PyrisFaqApi(PyrisWebhookService pyrisWebhookService) {
        this.pyrisWebhookService = pyrisWebhookService;
    }

    public void addFaq(Faq faq) {
        pyrisWebhookService.addFaq(faq);
    }

    public void deleteFaq(Faq faq) {
        pyrisWebhookService.deleteFaq(faq);
    }

    public void autoUpdateFaqInPyris(long courseId, Faq faq) {
        pyrisWebhookService.autoUpdateFaqInPyris(courseId, faq);
    }
}
