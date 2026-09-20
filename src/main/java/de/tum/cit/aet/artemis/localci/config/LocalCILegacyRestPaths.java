package de.tum.cit.aet.artemis.localci.config;

/**
 * The legacy URL prefixes the localci module still serves next to its canonical {@code api/localci/...} ones.
 * <p>
 * Everything else in this class was removed once no client called it any more. What is left is the one prefix that is not held open by a client at all, but by data: the Jenkins
 * job definitions of every installation.
 * <p>
 * Picked up automatically by {@code LegacyApiPathDeprecationInterceptor} through the multi-path {@code @RequestMapping} convention, and by
 * {@code FeatureUsageRegistry#canonicalPath}, which counts the endpoint under the canonical prefix, the first entry of the array.
 */
public final class LocalCILegacyRestPaths {

    /**
     * The prefix {@code PublicBuildPlanResource} was served under while it lived in the programming module. Successor: {@code api/localci/public/}.
     * <p>
     * <b>Pinned by deployed Jenkins job definitions, not by a client.</b> {@code JenkinsBuildPlanCreator} writes the URL a job fetches its build plan from into the job's own XML
     * at the moment the job is created, and from 8.0.0 (#10416) until the programming module was split (#12813, May 2026) it wrote
     * {@code {server}/api/programming/public/programming-exercises/{exerciseId}/build-plan?secret=...}. Every job created in that window still holds that string and still
     * requests it on every build, so removing this alias breaks building for exercises that nobody has touched since. Regenerating a job is what replaces the URL, and no
     * released version regenerates jobs on its own.
     * <p>
     * What retires it: {@code supporting_scripts/jenkins_path_migration/migrate_api_paths.sh}, which rewrites the URL in the stored job definitions, has a rule for this
     * spelling. Once an installation has run it, no job asks for this path any more, and the alias can go a release later.
     * <p>
     * Only the build plan endpoint needs it. The other path under the same old prefix,
     * {@code api/programming/public/programming-exercises/new-result}, is the canonical, single-valued mapping of {@code PublicProgrammingExerciseResultResource} and is not a
     * legacy alias of anything.
     */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String PROGRAMMING_PUBLIC_PREFIX = "api/programming/public/";

    private LocalCILegacyRestPaths() {
        // utility class
    }
}
