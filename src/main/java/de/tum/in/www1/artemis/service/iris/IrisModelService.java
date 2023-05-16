package de.tum.in.www1.artemis.service.iris;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.service.iris.model.IrisModel;

/**
 * Service to handle the different supported Iris models.
 */
@Service
public class IrisModelService {

    private final Optional<IrisModel> irisGPT3_5Service;

    public IrisModelService(Optional<IrisModel> irisGPT3_5Service) {
        this.irisGPT3_5Service = irisGPT3_5Service;
    }

    public CompletableFuture<Optional<IrisMessage>> requestResponse(IrisSession irisSession) {
        if (irisGPT3_5Service.isPresent()) {
            return irisGPT3_5Service.get().getResponse(irisSession).thenApply(Optional::ofNullable);
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
