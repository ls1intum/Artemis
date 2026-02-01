package de.tum.cit.aet.artemis.iris.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
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

    public void autoUpdateFaqInPyris(Faq faq) {
        pyrisWebhookService.autoUpdateFaqInPyris(faq);
    }
}
