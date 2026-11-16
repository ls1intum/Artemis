package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Issues and checks the credential a build agent uses to clone the repositories of one specific build job.
 * <p>
 * The token replaces the installation-wide build agent password on the https path. It is minted when the job is
 * queued, travels with the job, and is accepted only for the repositories that job declares and only while the job is
 * in the distributed processing list. Nothing here is time based: the build timeout already removes a job from that
 * list, so an expiry would only duplicate a bound the system already enforces, and would add a clock to disagree over.
 * <p>
 * The token is not a secret from the cluster. Any node that joins can read the build job queue, so this does not
 * defend against a hostile cluster member; the cluster password and the build agent network allowlist do. What it does
 * remove is the long lived shared secret: a token recovered from a build host is useless once that job ends, opens at
 * most that job's few repositories, and only from the address the owning agent is connected from.
 */
@Service
@Profile(PROFILE_LOCALCI)
@Lazy
public class BuildJobCloneTokenService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobCloneTokenService.class);

    /**
     * Marks the credential in a log line or a git configuration as a build job token rather than a user's password or
     * a VCS access token, which use the {@code vcpat-} prefix.
     */
    public static final String CLONE_TOKEN_PREFIX = "bjct-";

    /**
     * 64 random bytes. Unlike the VCS access tokens this is never stored in a {@code varchar(50)} column, so it is not
     * bound by their length.
     */
    private static final int TOKEN_BYTES = 64;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** The repositories of the database for a job, so that the clone requests of one job do not each read them. */
    private static final int MAX_CACHED_JOBS = 5000;

    private static final Duration CACHE_DURATION = Duration.ofMinutes(15);

    private final Cache<String, Set<RepositoryIdentity>> persistedRepositoriesOfJobs = Caffeine.newBuilder().maximumSize(MAX_CACHED_JOBS).expireAfterWrite(CACHE_DURATION).build();

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public BuildJobCloneTokenService(ParticipationRepository participationRepository, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.participationRepository = participationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Creates a token for a single build job.
     *
     * @return a fresh random token, prefixed so it is recognisable in a log
     */
    public String generateCloneToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return CLONE_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Checks a presented credential against the token of a build job.
     *
     * @param buildJob       the build job whose token to compare against, may be null
     * @param presentedToken the credential presented in the request, may be null
     * @return whether the job carries a token and the presented value matches it
     */
    public boolean tokenMatches(@Nullable BuildJobQueueItem buildJob, @Nullable String presentedToken) {
        if (buildJob == null || buildJob.cloneToken() == null || presentedToken == null) {
            return false;
        }
        // Time-constant, like the other credential comparisons on this path
        return MessageDigest.isEqual(buildJob.cloneToken().getBytes(StandardCharsets.UTF_8), presentedToken.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Collects every repository a build job legitimately reads: the participant's assignment repository, the tests,
     * the solution when the exercise checks it out, and any auxiliary repositories.
     * <p>
     * A token is accepted for these and nothing else, which is what turns "this caller is a build agent" into "this
     * caller is the build of this participation".
     * <p>
     * Each repository is identified by its project key and slug rather than by its full URI. The job's URIs are
     * persisted strings, written when the repository was created, while the requested URI is rebuilt from the current
     * {@code artemis.version-control.url}. An installation that changed that base URL after its participations were
     * created would compare a stale string against a fresh one and silently deny every clone; the project key and slug
     * survive such a change.
     * <p>
     * The repositories that the job declares are also compared with the database: the participation and the exercise of the job have to
     * match, and the project key and the slugs have to be those that the database holds for them. A declared repository that the database does not
     * hold for the exercise and the participation of the job is left out.
     *
     * @param buildJob the build job, may be null
     * @return the repositories of that job, empty if it has none
     */
    public Set<RepositoryIdentity> getRepositoryIdentities(@Nullable BuildJobQueueItem buildJob) {
        Set<RepositoryIdentity> declared = getDeclaredRepositoryIdentities(buildJob);
        if (declared.isEmpty()) {
            return declared;
        }
        declared.retainAll(getPersistedRepositoryIdentities(buildJob));
        return declared;
    }

    private Set<RepositoryIdentity> getDeclaredRepositoryIdentities(@Nullable BuildJobQueueItem buildJob) {
        if (buildJob == null || buildJob.repositoryInfo() == null) {
            return new HashSet<>();
        }
        RepositoryInfo repositoryInfo = buildJob.repositoryInfo();
        Set<RepositoryIdentity> repositories = new HashSet<>();
        addIfPresent(repositories, repositoryInfo.assignmentRepositoryUri());
        addIfPresent(repositories, repositoryInfo.testRepositoryUri());
        addIfPresent(repositories, repositoryInfo.solutionRepositoryUri());
        if (repositoryInfo.auxiliaryRepositoryUris() != null) {
            for (String auxiliaryRepositoryUri : repositoryInfo.auxiliaryRepositoryUris()) {
                addIfPresent(repositories, auxiliaryRepositoryUri);
            }
        }
        return repositories;
    }

    /**
     * The repositories that the database holds for the participation and the exercise of a job: the repository of the participation, and the template, solution, test and
     * auxiliary repositories of the exercise. Empty if the participation does not exist or does not belong to the exercise of the job.
     */
    private Set<RepositoryIdentity> getPersistedRepositoryIdentities(BuildJobQueueItem buildJob) {
        String jobId = buildJob.id();
        try {
            Set<RepositoryIdentity> persisted = persistedRepositoriesOfJobs.get(jobId, _ -> loadPersistedRepositoryIdentities(buildJob));
            if (persisted.isEmpty()) {
                // Nothing is remembered for a job that the database does not know, so that a later request reads it again
                persistedRepositoriesOfJobs.invalidate(jobId);
            }
            return persisted;
        }
        catch (DataAccessException e) {
            log.warn("Could not read the repositories of build job {} from the database, no repository is covered: {}", jobId, e.getMessage());
            return Set.of();
        }
    }

    private Set<RepositoryIdentity> loadPersistedRepositoryIdentities(BuildJobQueueItem buildJob) {
        Optional<Participation> participation = participationRepository.findWithProgrammingExerciseById(buildJob.participationId());
        if (participation.isEmpty() || !(participation.get() instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            return Set.of();
        }
        ProgrammingExercise participationExercise = programmingExerciseRepository.getProgrammingExerciseFromParticipation(programmingParticipation);
        if (participationExercise == null || !Objects.equals(participationExercise.getId(), buildJob.exerciseId())) {
            log.warn("The participation {} of build job {} does not belong to the exercise {} of the job", buildJob.participationId(), buildJob.id(), buildJob.exerciseId());
            return Set.of();
        }
        Optional<ProgrammingExercise> exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(buildJob.exerciseId());
        if (exercise.isEmpty()) {
            return Set.of();
        }
        Set<RepositoryIdentity> persisted = new HashSet<>();
        addIfPresent(persisted, programmingParticipation.getRepositoryUri());
        addIfPresent(persisted, exercise.get().getTemplateRepositoryUri());
        addIfPresent(persisted, exercise.get().getSolutionRepositoryUri());
        addIfPresent(persisted, exercise.get().getTestRepositoryUri());
        for (AuxiliaryRepository auxiliaryRepository : exercise.get().getAuxiliaryRepositories()) {
            addIfPresent(persisted, auxiliaryRepository.getRepositoryUri());
        }
        return persisted;
    }

    /**
     * Checks whether a build job may read the given repository.
     *
     * @param buildJob             the build job whose scope to check against, may be null
     * @param localVCRepositoryUri the repository being requested
     * @return whether that repository is one of the job's own
     */
    public boolean coversRepository(@Nullable BuildJobQueueItem buildJob, LocalVCRepositoryUri localVCRepositoryUri) {
        if (buildJob == null) {
            return false;
        }
        RepositoryIdentity requested = RepositoryIdentity.of(localVCRepositoryUri);
        // The declaration is compared first, so that a repository that the job does not declare costs no database access
        if (!getDeclaredRepositoryIdentities(buildJob).contains(requested)) {
            return false;
        }
        return getPersistedRepositoryIdentities(buildJob).contains(requested);
    }

    private static void addIfPresent(Set<RepositoryIdentity> repositories, @Nullable String repositoryUri) {
        if (repositoryUri == null || repositoryUri.isBlank()) {
            return;
        }
        try {
            repositories.add(RepositoryIdentity.of(new LocalVCRepositoryUri(repositoryUri)));
        }
        catch (RuntimeException e) {
            // A URI that cannot be parsed cannot be matched either. Skipping it denies access to that one repository
            // rather than failing the whole request, which would take the job's other repositories down with it.
            log.warn("Ignoring unparsable repository uri '{}' while determining what a build job may read", repositoryUri, e);
        }
    }

    /**
     * Identifies a repository by the two parts that do not depend on the configured base URL.
     *
     * @param projectKey     the project key of the exercise
     * @param repositorySlug the slug of the individual repository
     */
    public record RepositoryIdentity(String projectKey, String repositorySlug) {

        static RepositoryIdentity of(LocalVCRepositoryUri localVCRepositoryUri) {
            return new RepositoryIdentity(localVCRepositoryUri.getProjectKey(), localVCRepositoryUri.repositorySlug());
        }
    }
}
