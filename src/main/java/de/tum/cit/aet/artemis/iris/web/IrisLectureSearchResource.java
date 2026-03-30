package de.tum.cit.aet.artemis.iris.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchAskResponseDTO;

/**
 * REST controller for Iris lecture search.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisLectureSearchResource {

    private final PyrisConnectorService pyrisConnectorService;

    public IrisLectureSearchResource(PyrisConnectorService pyrisConnectorService) {
        this.pyrisConnectorService = pyrisConnectorService;
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
     *
     * @param requestDTO the request containing the query and result limit
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the answer with sources
     */
    @PostMapping("search-answer")
    @EnforceAtLeastStudent
    public ResponseEntity<PyrisSearchAskResponseDTO> ask(@RequestBody @Valid PyrisSearchAskRequestDTO requestDTO) {
        return ResponseEntity.ok(pyrisConnectorService.searchAsk(requestDTO.query(), requestDTO.limit()));
    }
}
