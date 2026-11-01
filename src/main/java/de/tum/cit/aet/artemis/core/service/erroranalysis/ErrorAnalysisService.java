package de.tum.cit.aet.artemis.core.service.erroranalysis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;

/**
 * The error analysis of the server and the clients, which groups the errors that are alike and counts them.
 * <p>
 * The errors of the server reach {@link ErrorAnalysisStore} through the log, see {@code ErrorAnalysisAppender}. The errors that
 * clients report are recorded here, because the log entry of a report is not an error of the server. Nothing is stored in the
 * database, see {@link ErrorAnalysisStore} for what that means.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ErrorAnalysisService {

    private final ErrorAnalysisStore store = ErrorAnalysisStore.shared();

    /**
     * Counts an error that a client reported.
     *
     * @param severity how serious the client says the error is
     * @param message  what went wrong, already cleaned of personal data
     */
    public void recordClientError(ErrorSeverity severity, String message) {
        store.record(ErrorLocation.CLIENT, severity, message);
    }

    /**
     * The groups of the errors that happened in a time span.
     *
     * @param span        how far to look back from now
     * @param location    only the errors of this location, or {@code null} for all of them
     * @param minSeverity only the errors that are at least this serious
     * @return the groups, the most frequent first
     */
    public List<ErrorGroupDTO> getGroups(ErrorAnalysisSpan span, ErrorLocation location, ErrorSeverity minSeverity) {
        return store.query(span, location, minSeverity);
    }
}
