package de.tum.cit.aet.artemis.communication.service;

import java.util.Optional;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;

/**
 * Lets tests in other packages build the scheduling-only reconcile service and run single passes.
 */
public final class FaqIngestionReconcileServiceTestAccess {

    private final FaqIngestionReconcileService service;

    private FaqIngestionReconcileServiceTestAccess(FaqIngestionReconcileService service) {
        this.service = service;
    }

    public static FaqIngestionReconcileServiceTestAccess create(FaqRepository faqRepository, Optional<PyrisFaqApi> pyrisFaqApi, Optional<IrisSettingsApi> irisSettingsApi,
            int maxActionsPerRun) {
        return new FaqIngestionReconcileServiceTestAccess(new FaqIngestionReconcileService(faqRepository, pyrisFaqApi, irisSettingsApi, maxActionsPerRun));
    }

    public int reconcile() {
        return service.reconcile();
    }
}
