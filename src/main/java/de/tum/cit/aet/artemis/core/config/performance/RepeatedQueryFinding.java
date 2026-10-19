package de.tum.cit.aet.artemis.core.config.performance;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A query template that ran more than once within a single HTTP request. Reported as one of two
 * yes/no shapes -- deliberately without a repetition threshold, since either shape is wasteful
 * whether it happens 2 or 200 times (how often it repeats depends on the test's data volume, the
 * shape itself does not):
 * <ul>
 * <li>{@link Type#N_PLUS_ONE} -- one piece of code ran the template with at least two different
 * parameter sets, i.e. one query per item instead of one query for all items. Its executions
 * include any repeats of the same parameter set.</li>
 * <li>{@link Type#DUPLICATE} -- the exact same statement with the exact same parameters ran
 * more than once, i.e. the same data was loaded again: either by one piece of code (named by
 * {@code repositoryMethod} and {@code callerMethod}), or by several (listed in {@code callSites},
 * with the other two empty).</li>
 * </ul>
 *
 * @param type                  which of the two shapes this finding reports.
 * @param normalizedSql         the query template, literals stripped.
 * @param executions            how many times the template ran in the request; for a duplicate
 *                                  across several pieces of code, only the executions of the
 *                                  statements more than one of them ran.
 * @param distinctParameterSets how many different parameter sets those executions used; for
 *                                  {@link Type#DUPLICATE}, {@code executions - distinctParameterSets}
 *                                  is the number of redundant executions.
 * @param repositoryMethod      see {@link QueryCallSite#repositoryMethod()}; may be {@code null}.
 * @param callerMethod          see {@link QueryCallSite#callerMethod()}; may be {@code null}.
 * @param httpMethod            HTTP verb of the request.
 * @param httpEndpoint          route template of the request, e.g. {@code /api/courses/{courseId}}.
 * @param testName              Playwright test name from the {@code X-Playwright-Test-Name} header; may be {@code null}.
 * @param phase                 Playwright phase from the {@code X-Playwright-Phase} header; may be {@code null}.
 * @param callSites             for a duplicate across several pieces of code, each of them; empty otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepeatedQueryFinding(Type type, String normalizedSql, int executions, int distinctParameterSets, String repositoryMethod, String callerMethod, String httpMethod,
        String httpEndpoint, String testName, String phase, List<QueryCallSite> callSites) {

    /** The two repetition shapes, see the class documentation. */
    public enum Type {
        N_PLUS_ONE, DUPLICATE
    }
}
