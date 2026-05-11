package de.tum.cit.aet.artemis.iris.web;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchAskRequestDTO;

/**
 * REST controller for Iris lecture search.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisLectureSearchResource {

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    public IrisLectureSearchResource(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, UserRepository userRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
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
     * POST api/iris/search-answer: Ask Iris to answer a question using course content (async).
     * Pyris classifies the query and sends webhook callbacks; results are pushed to the client via WebSocket.
     *
     * @param requestDTO the request containing the query and result limit
     * @param principal  the authenticated user (used to route the WebSocket response)
     * @return the {@link ResponseEntity} with status {@code 202 (Accepted)}
     */
    @PostMapping("search-answer")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, String>> ask(@RequestBody @Valid PyrisSearchAskRequestDTO requestDTO, Principal principal) {
        var user = userRepository.findOneByLogin(principal.getName()).orElseThrow();
        var jobToken = pyrisJobService.addGlobalSearchAnswerJob(principal.getName());
        // Note: do NOT remove the job on exception here. Transport-level failures are ambiguous —
        // Pyris may have received the request and already started the pipeline. Removing the token
        // would break WebSocket routing for any callbacks that arrive later.
        // Jobs expire automatically via the Hazelcast TTL (default 5 minutes).
        pyrisConnectorService.executeGlobalSearchIrisAnswer(requestDTO.query(), requestDTO.limit(), jobToken, user.getSelectedLLMUsage());
        return ResponseEntity.accepted().body(Map.of("runId", jobToken));
    }
}
