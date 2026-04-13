package de.tum.cit.aet.artemis.iris.web;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.IrisSearchAskClientRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

/**
 * REST controller for Iris lecture search.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisLectureSearchResource {

    @Value("${server.url}")
    private String artemisBaseUrl;

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    public IrisLectureSearchResource(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
    }

    /**
     * POST api/iris/lecture-search: Search for lecture units using Pyris.
     *
     * @param requestDTO the search request containing query and limit
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of search results
     */
    @PostMapping("lecture-search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PyrisLectureSearchResultDTO>> search(@RequestBody @Valid PyrisLectureSearchRequestDTO requestDTO) {
        return ResponseEntity.ok(pyrisConnectorService.searchLectures(requestDTO.query(), requestDTO.limit()));
    }

    /**
     * POST api/iris/search-answer: Ask Iris to answer a question using lecture content.
     * Returns immediately with a job token. Pyris processes the request asynchronously and posts
     * results back via the callback endpoint, which forwards them to the client over WebSocket.
     *
     * @param requestDTO the request containing the query and result limit
     * @return the {@link ResponseEntity} with status {@code 202 (Accepted)} and the job token
     */
    @PostMapping("search-answer")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, String>> ask(@RequestBody @Valid IrisSearchAskClientRequestDTO requestDTO) {
        var userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow();
        var token = pyrisJobService.addSearchAnswerJob(userLogin);
        pyrisConnectorService.searchAsk(requestDTO.query(), requestDTO.limit(), artemisBaseUrl, token);
        return ResponseEntity.accepted().body(Map.of("token", token));
    }
}
