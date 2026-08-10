package de.tum.cit.aet.artemis.iris.api;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisLectureSnippetDTO;
import de.tum.cit.aet.artemis.iris.service.IrisAccessContextService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisLectureSearchApi extends AbstractIrisApi {

    private final PyrisConnectorService pyrisConnectorService;

    private final UserRepository userRepository;

    private final IrisAccessContextService irisAccessContextService;

    public IrisLectureSearchApi(PyrisConnectorService pyrisConnectorService, UserRepository userRepository, IrisAccessContextService irisAccessContextService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.userRepository = userRepository;
        this.irisAccessContextService = irisAccessContextService;
    }

    /**
     * Performs a semantic lecture search via Pyris on behalf of the current user, optionally scoped to a set of courses.
     * <p>
     * The requesting user's role-grouped course access is resolved and forwarded to Pyris so unreleased lecture content stays visible exactly where the user may see it (staff or
     * admin of a course), matching the Artemis UI. Forwarding a resolved context is mandatory here: an absent context makes Pyris apply the safe-default visibility filter
     * (released
     * content only), which would hide unreleased units even from authorized staff callers such as Hyperion's editor-only generation flow.
     *
     * @param query     the search query
     * @param limit     maximum number of results to return
     * @param courseIds optional list of course IDs to restrict the search to; {@code null} means search all ingested courses the user can access
     * @return list of matching lecture snippets
     */
    public List<IrisLectureSnippetDTO> searchLectures(String query, int limit, @Nullable List<Long> courseIds) {
        var user = userRepository.getUserWithCourseRolesAndAuthorities();
        var accessContext = irisAccessContextService.resolveAccessContext(user);
        return pyrisConnectorService.searchLectures(query, limit, courseIds, accessContext).stream()
                .map(r -> new IrisLectureSnippetDTO(r.lecture().name(), r.lectureUnit().name(), r.snippet())).toList();
    }
}
