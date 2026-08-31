package de.tum.cit.aet.artemis.admin.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.Path;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.admin.service.export.DataExportService;
import de.tum.cit.aet.artemis.core.api.AbstractApi;

/**
 * API boundary for account-lifecycle operations that need to remove generated data exports.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class DataExportApi implements AbstractApi {

    private final DataExportService dataExportService;

    public DataExportApi(DataExportService dataExportService) {
        this.dataExportService = dataExportService;
    }

    /**
     * Deletes all export records for a user.
     *
     * @param userId the user whose exports should be deleted
     * @return archive paths that the caller must delete after its transaction commits
     */
    public List<Path> deleteAllForUser(long userId) {
        return dataExportService.deleteAllForUser(userId);
    }
}
