package de.tum.cit.aet.artemis.iris.api;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.dto.FaqCensusDTO;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class PyrisFaqApi extends AbstractIrisApi {

    private final PyrisWebhookService pyrisWebhookService;

    private final PyrisConnectorService pyrisConnectorService;

    public PyrisFaqApi(PyrisWebhookService pyrisWebhookService, PyrisConnectorService pyrisConnectorService) {
        this.pyrisWebhookService = pyrisWebhookService;
        this.pyrisConnectorService = pyrisConnectorService;
    }

    public void addFaq(Faq faq) {
        pyrisWebhookService.addFaq(faq);
    }

    public void deleteFaq(Faq faq) {
        pyrisWebhookService.deleteFaq(faq);
    }

    /**
     * Delete an FAQ from Pyris by its ids alone, for an FAQ that no longer exists in Artemis or is not accepted anymore.
     *
     * @param faqId    the FAQ to delete
     * @param courseId its course
     */
    public void deleteFaq(long faqId, long courseId) {
        pyrisWebhookService.deleteFaq(faqId, courseId);
    }

    /**
     * Fetch the FAQ census of this Artemis instance from Pyris.
     *
     * @return the census, or {@code null} when it is unavailable
     */
    @Nullable
    public FaqCensusDTO getFaqCensus() {
        return pyrisConnectorService.getFaqCensus();
    }

    public void autoUpdateFaqInPyris(Faq faq) {
        pyrisWebhookService.autoUpdateFaqInPyris(faq);
    }
}
