package de.tum.cit.aet.artemis.iris.api;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisLectureSnippetDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;

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
     *
     * @param query     the search query
     * @param limit     maximum number of results to return
     * @param courseIds optional list of course IDs to restrict the search to; {@code null} means search all ingested courses
     * @return list of matching lecture snippets
     */
    public List<IrisLectureSnippetDTO> searchLectures(String query, int limit, @Nullable List<Long> courseIds) {
        return pyrisConnectorService.searchLectures(query, limit, courseIds).stream().map(r -> new IrisLectureSnippetDTO(r.lecture().name(), r.lectureUnit().name(), r.snippet()))
                .toList();
    }
}
