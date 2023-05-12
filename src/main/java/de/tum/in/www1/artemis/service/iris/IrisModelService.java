package de.tum.in.www1.artemis.service.iris;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.service.iris.model.IrisGPT3_5Service;

/**
 * Service to handle the different supported Iris models.
 */
@Service
public class IrisModelService {

    private final Optional<IrisGPT3_5Service> irisGPT3_5Service;

    public IrisModelService(Optional<IrisGPT3_5Service> irisGPT3_5Service) {
        this.irisGPT3_5Service = irisGPT3_5Service;
    }

    @Async
    public CompletableFuture<Optional<IrisMessage>> getResponse(IrisSession irisSession) {
        if (irisGPT3_5Service.isPresent()) {
            try {
                var response = irisGPT3_5Service.get().getResponse(irisSession).get();
                return CompletableFuture.completedFuture(Optional.of(response));
            }
            catch (InterruptedException | ExecutionException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
