package de.tum.cit.aet.artemis.iris.api;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisLectureSnippetDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
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
     * Performs a semantic lecture search via Pyris, optionally scoped to a set of courses.
     * Used by quiz generation (Louis Heinrich) — passes no access context, only a course ID list.
     *
     * @param query     the search query
     * @param limit     maximum number of results to return
     * @param courseIds optional list of course IDs to restrict the search to; {@code null} means search all ingested courses
     * @return list of matching lecture snippets
     */
    public List<IrisLectureSnippetDTO> searchLectures(String query, int limit, @Nullable List<Long> courseIds) {
        return pyrisConnectorService.searchLectures(query, limit, courseIds, null).stream()
                .map(r -> new IrisLectureSnippetDTO(r.lecture().name(), r.lectureUnit().name(), r.snippet())).toList();
    }

    /**
     * Performs a role-aware lecture search via Pyris using a resolved access context.
     * Used by the global search UI — Artemis resolves the user's course memberships into
     * role-keyed course ID sets, and Pyris applies them as opaque Weaviate filters.
     *
     * @param query         the search query
     * @param limit         maximum number of results to return
     * @param courseIds     optional list of course IDs to restrict the search to; {@code null} means no course filter
     * @param accessContext role-based course ID sets resolved by Artemis; {@code null} means admin (no filter)
     * @return list of matching lecture search results with full metadata
     */
    public List<PyrisLectureSearchResultDTO> searchLecturesByAccessContext(String query, int limit, @Nullable List<Long> courseIds, @Nullable PyrisAccessContextDTO accessContext) {
        return pyrisConnectorService.searchLectures(query, limit, courseIds, accessContext);
    }
}
