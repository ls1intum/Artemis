package de.tum.cit.aet.artemis.iris.web.internal;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.Internal;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;

/**
 * REST controller providing Pyris with live Artemis data lookups.
 * All endpoints use the same job-token authentication as {@link PyrisInternalStatusUpdateResource}.
 */
@Lazy
@RestController
@Conditional(IrisEnabled.class)
@RequestMapping("api/iris/internal/")
public class PyrisInternalCourseResource {

    private final PyrisJobService pyrisJobService;

    private final CourseRepository courseRepository;

    public PyrisInternalCourseResource(PyrisJobService pyrisJobService, CourseRepository courseRepository) {
        this.pyrisJobService = pyrisJobService;
        this.courseRepository = courseRepository;
    }

    /**
     * {@code GET api/iris/internal/courses/names} : Returns current course titles for the given IDs.
     * <p>
     * Called by Pyris after Weaviate retrieval to enrich the top-N merged results with up-to-date
     * course names. Only the course IDs that actually appear in the final result set are requested,
     * so the DB lookup is minimal (typically 1–5 courses).
     *
     * @param ids     the course IDs to look up
     * @param request the HTTP request (used for Pyris job-token authentication)
     * @return a map of courseId → courseTitle for each requested ID that exists
     */
    @GetMapping("courses/names")
    @Internal
    public ResponseEntity<Map<Long, String>> getCourseNames(@RequestParam List<Long> ids, HttpServletRequest request) {
        pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(request, PyrisJob.class);
        if (ids.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }
        var names = courseRepository.findAllById(ids).stream().filter(c -> c.getTitle() != null).collect(Collectors.toMap(c -> c.getId(), c -> c.getTitle()));
        return ResponseEntity.ok(names);
    }
}
