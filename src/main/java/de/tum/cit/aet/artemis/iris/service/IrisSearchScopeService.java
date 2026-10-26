package de.tum.cit.aet.artemis.iris.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * Resolves the courses a global search covers: the courses the caller asked for, limited to the courses the caller can access, minus the ones the caller asked to hide.
 * <p>
 * Used by the lecture content search and by the Iris answer, so both apply the same rules to the course filters of a request.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisSearchScopeService {

    private static final String ENTITY_NAME = "iris";

    private final IrisSettingsService irisSettingsService;

    public IrisSearchScopeService(IrisSettingsService irisSettingsService) {
        this.irisSettingsService = irisSettingsService;
    }

    /**
     * The resolved search scope: the courses to search, the exclusions Pyris still has to apply, and whether there is anything left to search at all.
     *
     * @param courseIds        the courses to search, or {@code null} to leave an unrestricted caller unscoped
     * @param excludeCourseIds the courses Pyris has to hide itself, or {@code null} when Artemis already removed them from {@link #courseIds}
     * @param searchesNothing  whether the caller's exclusions, the access check or a missing course left no course to search, which is answered without asking Pyris
     */
    public record LectureSearchScope(@Nullable List<Long> courseIds, @Nullable List<Long> excludeCourseIds, boolean searchesNothing) {

        static LectureSearchScope nothing() {
            return new LectureSearchScope(null, null, true);
        }

        static LectureSearchScope of(@Nullable List<Long> courseIds, @Nullable List<Long> excludeCourseIds) {
            return new LectureSearchScope(courseIds, excludeCourseIds, false);
        }
    }

    /**
     * Narrows a search to the courses the caller asked for, minus the ones it asked to hide, and, for the Iris answer, to the courses whose Iris
     * course settings are enabled.
     * <p>
     * The courses the caller asked for are validated against the courses the caller can access: an id the caller has no access to is dropped, so
     * the list sent to Pyris is always a subset of the access context and never relies on Pyris to narrow it. Only an unrestricted caller is
     * not limited this way.
     * <p>
     * The Iris answer is an interactive Iris feature, like the chat it can be continued in, so it must not draw on a course whose instructor
     * switched Iris off. An unscoped request cannot be forwarded untouched for it: Pyris falls back to the access context when it receives no
     * course list, and that context is built from course roles alone. Switching Iris off does not remove what was already ingested, and the
     * content of such a course stays searchable, which is why the scope has to be narrowed here rather than relying on an empty index.
     * <p>
     * The narrowed scope is never forwarded empty: {@code PyrisLectureSearchRequestDTO} omits an empty list on the wire and
     * Pyris reads an absent list as unscoped, so a caller whose courses all have Iris switched off is refused instead.
     *
     * @param requestedCourseIds the course IDs the client asked for, {@code null} or empty for an unscoped search
     * @param excludedCourseIds  the course IDs the client asked to hide, {@code null} or empty when nothing is hidden
     * @param accessContext      the caller's resolved access context
     * @param onlyIrisEnabled    whether to keep only courses with Iris enabled, which the Iris answer needs and the content search does not
     * @return the scope to search, see {@link LectureSearchScope}
     * @throws AccessForbiddenAlertException if {@code onlyIrisEnabled} is set and Iris is disabled for every course in the scope
     */
    public LectureSearchScope resolveScope(@Nullable List<Long> requestedCourseIds, @Nullable List<Long> excludedCourseIds, PyrisAccessContextDTO accessContext,
            boolean onlyIrisEnabled) {
        var hiddenByRequest = excludedCourseIds == null ? List.<Long>of() : excludedCourseIds;
        boolean isUnscoped = requestedCourseIds == null || requestedCourseIds.isEmpty();
        if (isUnscoped && accessContext.unrestricted()) {
            // An unrestricted caller carries no course list to narrow, so Pyris keeps its own no-ceiling behaviour. With no
            // ceiling to subtract from, it is also the only caller whose exclusions have to be applied by the query itself,
            // including the courses with Iris switched off when the Iris answer asks.
            var hiddenCourseIds = onlyIrisEnabled ? Stream.concat(hiddenByRequest.stream(), irisSettingsService.findCourseIdsWithIrisDisabled().stream()).distinct().toList()
                    : hiddenByRequest;
            return LectureSearchScope.of(null, hiddenCourseIds.isEmpty() ? null : hiddenCourseIds);
        }
        // An unscoped request is narrowed from every course the caller can access, a scoped one from what it asked for.
        var candidateCourseIds = isUnscoped ? accessContext.courseIds() : requestedCourseIds;
        // Every other caller travels with a ceiling, so a hidden course is subtracted here and never named to Pyris. A course the caller cannot
        // access is dropped the same way, which only changes a scoped request: an unscoped one already starts from the accessible courses. An
        // unrestricted caller lists no courses in its context, but is not limited to any, so it keeps every course it asked for.
        var accessibleCourseIds = Set.copyOf(accessContext.courseIds());
        var scopedCourseIds = candidateCourseIds.stream().filter(courseId -> !hiddenByRequest.contains(courseId))
                .filter(courseId -> accessContext.unrestricted() || accessibleCourseIds.contains(courseId)).toList();
        if (scopedCourseIds.isEmpty()) {
            // The exclusions or the access check removed every course in scope, or the caller has no course at all. An empty list is dropped on
            // the wire and read as unscoped, so answering here is what keeps "hide all of them" from searching all of them.
            return LectureSearchScope.nothing();
        }
        if (!onlyIrisEnabled) {
            return LectureSearchScope.of(scopedCourseIds, null);
        }
        var enabledCourseIds = irisSettingsService.filterCourseIdsWithIrisEnabled(scopedCourseIds);
        if (enabledCourseIds.isEmpty()) {
            // suppress the error alert with skipAlert: true so that the client can fall back to its standard metadata search
            throw new AccessForbiddenAlertException(ErrorConstants.DEFAULT_TYPE, "Iris is disabled for every course in the search scope", ENTITY_NAME, "iris.course_disabled",
                    true);
        }
        return LectureSearchScope.of(enabledCourseIds, null);
    }
}
