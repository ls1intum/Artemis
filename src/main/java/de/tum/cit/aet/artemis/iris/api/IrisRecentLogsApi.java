package de.tum.cit.aet.artemis.iris.api;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisLogEntryDTO;

/**
 * API for the admin module to read Iris's recent ingestion log records.
 * <p>
 * The admin log view merges them with Artemis's own so an administrator reads one ordered story across both
 * services; see {@code AdminLogResource#getIngestionLogs}.
 */
@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisRecentLogsApi extends AbstractIrisApi {

    private final PyrisConnectorService pyrisConnectorService;

    public IrisRecentLogsApi(PyrisConnectorService pyrisConnectorService) {
        this.pyrisConnectorService = pyrisConnectorService;
    }

    /**
     * Reads Iris's recent ingestion records.
     *
     * @param limit how many records to ask for, newest first
     * @param level return only records at exactly this level, or null for every level
     * @return the records, or empty when Iris is unreachable
     */
    public List<PyrisLogEntryDTO> getRecentLogs(int limit, @Nullable String level) {
        return pyrisConnectorService.getRecentLogs(limit, level);
    }
}
