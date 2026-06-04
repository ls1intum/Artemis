package de.tum.cit.aet.artemis.iris.api;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisLectureSearchApi extends AbstractIrisApi {

    private final PyrisConnectorService pyrisConnectorService;

    public IrisLectureSearchApi(PyrisConnectorService pyrisConnectorService) {
        this.pyrisConnectorService = pyrisConnectorService;
    }

    /**
     * Search for lecture content matching the query.
     *
     * @param query     the search query
     * @param limit     maximum number of results to return
     * @param courseIds optional list of course IDs to restrict the search; null means global search across all accessible content
     * @return list of matching lecture search results
     */
    public List<PyrisLectureSearchResultDTO> searchLectures(String query, int limit, @Nullable List<Long> courseIds) {
        return pyrisConnectorService.searchLectures(query, limit, courseIds, null);
    }
}
