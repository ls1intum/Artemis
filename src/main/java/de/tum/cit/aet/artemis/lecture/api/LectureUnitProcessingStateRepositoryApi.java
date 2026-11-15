package de.tum.cit.aet.artemis.lecture.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Read-only access to the lecture unit processing state ledger for other modules.
 * <p>
 * Deliberately backed by the repository alone: the iris module authenticates ingestion callbacks against
 * this ledger, and routing that lookup through {@link ProcessingStateCallbackApi} would close a bean cycle
 * (the callback service dispatches through the iris module, whose job service authenticates through the
 * callback service).
 */
@Conditional(LectureWithIrisEnabled.class)
@Controller
@Lazy
public class LectureUnitProcessingStateRepositoryApi extends AbstractLectureApi {

    private final LectureUnitProcessingStateRepository processingStateRepository;

    public LectureUnitProcessingStateRepositoryApi(LectureUnitProcessingStateRepository processingStateRepository) {
        this.processingStateRepository = processingStateRepository;
    }

    /**
     * Resolve the identity of the ingestion run that owns a job token.
     *
     * @param token the Pyris job token of an in-flight ingestion run
     * @return the course, lecture and unit the run belongs to, empty when no in-flight run owns the token
     */
    public Optional<IngestionJobIdentityDTO> findIngestionJobIdentityByToken(String token) {
        return processingStateRepository.findIngestionJobIdentityByToken(token);
    }
}
