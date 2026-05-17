package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;

/**
 * Ensures the build-container cache directories exist and are deletable by the agent JVM, independent of the Docker
 * build container's user identity.
 * <p>
 * <h2>The permission problem</h2>
 * The Maven and Gradle cache directories are bind-mounted into every build container. Build containers in this code
 * base run as their image's default user — root in most Artemis-provided images. When the container creates a file
 * inside the bind-mount, the host sees that file owned by {@code root} (UID&nbsp;0) because container-root is mapped
 * to host-root on a regular bind-mount (no user-namespace remap). The agent JVM, on the other hand, runs as the
 * unprivileged {@code artemis} service user. Without intervention, the JVM cannot delete those root-owned files at
 * cleanup or wipe time — every {@code Files.delete} returns {@code AccessDeniedException} and the wipe reports
 * {@code errors=N} while freeing zero bytes.
 * <p>
 * <h2>The POSIX-ACL fix</h2>
 * This initializer applies a <em>POSIX default ACL</em> on each cache directory that grants the agent's own user
 * read/write/execute on any descendant. Files created later by container-root inherit this default ACL on the host,
 * so the agent JVM can read and delete them at cleanup or wipe time. {@code setfacl(1)} is the only practical way to
 * set POSIX ACLs from a JVM — Java's NIO {@code AclFileAttributeView} only supports NFSv4-style ACLs and does not
 * work for POSIX 1e ACLs on Linux. We shell out via {@link ProcessBuilder} once at startup; the operation is
 * idempotent so re-running it on every JVM restart is safe.
 * <p>
 * <h2>Order of operations</h2>
 * <ol>
 * <li>Detect each configured cache root. If neither Maven nor Gradle cache is configured (e.g. cache feature
 * disabled), this initializer is a no-op.</li>
 * <li>Create the directory if missing via {@code Files.createDirectories}. The new directory is owned by the JVM's
 * user (the {@code artemis} service user), so the subsequent {@code setfacl} runs without elevated privileges.</li>
 * <li>If {@code setfacl} is on {@code PATH}, apply <em>both</em> an access ACL (handles files Maven/Gradle have
 * already deposited in pre-existing caches) and a default ACL (handles files Maven/Gradle will deposit in the
 * future). The {@code -R} flag walks existing files; the {@code -d} variant sets the default ACL inherited by
 * future children.</li>
 * <li>If {@code setfacl} is missing or returns a non-zero exit code, log a clear actionable {@code WARN} with the
 * exact command an operator can run to remediate. We do not fail the JVM startup — the agent can still serve
 * builds, but the operator-triggered wipe / cleanup paths will report errors when they try to delete root-owned
 * files.</li>
 * </ol>
 * <p>
 * <h2>Why we do not change the build container's user identity</h2>
 * Switching the build container itself to run as the {@code artemis} UID would solve the ownership issue more
 * fundamentally — files would be created as {@code artemis} in the first place. But several of the supported
 * programming-language images either have a hard-coded HOME of {@code /root} (so the cache mount path
 * {@code /root/.m2} stops being writable by a non-root container user) or assume root for {@code apt-get install}
 * during build setup. Migrating those images is out of scope for the cache-permission fix. The default-ACL approach
 * leaves the existing build images entirely untouched.
 * <p>
 * <h2>Operator visibility</h2>
 * Every action this class takes is logged so an operator (or AI agent debugging a wipe that freed 0 bytes) can tell
 * exactly what happened from the agent log alone — directory creation, setfacl exit code, and missing-binary
 * fallback are all surfaced.
 */
@Service
@Profile(PROFILE_BUILDAGENT)
@Lazy(false)
public class BuildAgentCacheDirectoryInitializer {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentCacheDirectoryInitializer.class);

    private static final Set<PosixFilePermission> CACHE_DIRECTORY_PERMISSIONS = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE);

    /** Cap on how long we wait for a single {@code setfacl} invocation. ACL walks on huge caches can be slow. */
    private static final long SETFACL_TIMEOUT_SECONDS = 30;

    private final BuildAgentConfiguration buildAgentConfiguration;

    public BuildAgentCacheDirectoryInitializer(BuildAgentConfiguration buildAgentConfiguration) {
        this.buildAgentConfiguration = buildAgentConfiguration;
    }

    @PostConstruct
    void initializeCacheDirectories() {
        initializeOne("Maven", buildAgentConfiguration.mavenCacheHostPath());
        initializeOne("Gradle", buildAgentConfiguration.gradleCacheHostPath());
    }

    private void initializeOne(String label, Path cacheRoot) {
        if (cacheRoot == null) {
            log.debug("{} cache path is not configured; skipping cache directory initialisation.", label);
            return;
        }
        try {
            ensureDirectoryExists(label, cacheRoot);
            applyDefaultAcl(label, cacheRoot);
        }
        catch (RuntimeException unexpected) {
            // We never want a startup hiccup here to take the agent down. The wipe/cleanup paths will surface the
            // permission problem to operators clearly when they next run.
            log.warn("Unexpected error during {} cache directory initialisation; the agent will still start, but cleanup of root-owned files may fail. Path: {}", label, cacheRoot,
                    unexpected);
        }
    }

    private void ensureDirectoryExists(String label, Path cacheRoot) {
        if (Files.isDirectory(cacheRoot)) {
            log.debug("{} cache directory already exists at {}", label, cacheRoot);
            return;
        }
        try {
            Files.createDirectories(cacheRoot);
            // Set mode 0775 so the cache is readable cluster-wide on the host. Group permissions are not relied on
            // for the JVM ↔ container interaction (that is the ACL's job) but make `du`, `ls`, and other operator
            // tools work without sudo.
            try {
                Files.setPosixFilePermissions(cacheRoot, CACHE_DIRECTORY_PERMISSIONS);
            }
            catch (UnsupportedOperationException | IOException permError) {
                // Non-POSIX filesystem (e.g. Docker-mounted overlay on Windows). Directory exists, that's enough.
                log.debug("Could not set POSIX permissions on {}: {}", cacheRoot, permError.getMessage());
            }
            log.info("Created {} cache directory at {} (owned by the agent JVM's user)", label, cacheRoot);
        }
        catch (IOException createError) {
            // Most likely: the parent directory does not exist or is not writable by the JVM user. Tell the
            // operator both the path we tried and the exception so they can pick the right fix (chown the parent,
            // pre-create with the right ownership via Ansible, etc.).
            log.warn("Could not create {} cache directory at {}: {}. The agent will start, but builds that rely on a host-side cache mount will fail until the directory exists.",
                    label, cacheRoot, createError.getMessage());
        }
    }

    /**
     * Apply both an access ACL (covering files already in the directory) and a default ACL (covering files that will
     * be created later by the build container's root user) granting the agent's own user read+write+execute. The
     * default ACL is what makes the operator-triggered cache wipe / cleanup actually deletable later.
     */
    private void applyDefaultAcl(String label, Path cacheRoot) {
        if (!Files.isDirectory(cacheRoot)) {
            // ensureDirectoryExists already logged a warning if creation failed.
            return;
        }
        String user = currentUser();
        if (user == null) {
            log.warn("Could not determine the current OS user; skipping ACL setup on {}. Manual fix: 'sudo setfacl -R -m u:<artemis-user>:rwx -d -m u:<artemis-user>:rwx {}'.",
                    cacheRoot, cacheRoot);
            return;
        }
        // -R applies to existing children, -m sets the access ACL, -d -m sets the default ACL for future children.
        // We run both as a single invocation so they are atomic from the operator's perspective; setfacl supports
        // mixing -m and -d -m on the same command line.
        boolean ok = runSetfacl(label, cacheRoot, "-R", "-m", "u:" + user + ":rwx", "-d", "-m", "u:" + user + ":rwx");
        if (ok) {
            log.info("{} cache directory at {} has been ACL-prepared so the agent JVM can later delete files created by root-owned build containers (user: {})", label, cacheRoot,
                    user);
        }
    }

    /**
     * Invoke {@code setfacl} with the given arguments. Returns {@code true} on success. On any failure mode — binary
     * not on {@code PATH}, non-zero exit, timeout, IO error — logs a {@code WARN} with the exact manual fix and
     * returns {@code false}. The agent continues to start regardless; the operator can either install the
     * {@code acl} package and restart the agent, or run the printed command once by hand.
     */
    private boolean runSetfacl(String label, Path cacheRoot, String... args) {
        String[] full = buildSetfaclCommand(cacheRoot, args);
        ProcessBuilder pb = new ProcessBuilder(full).redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        }
        catch (IOException missingBinary) {
            log.warn(
                    "'setfacl' is not available on PATH; cannot grant the agent JVM delete permission on the {} cache at {}. "
                            + "Install the 'acl' package on this host (Ubuntu/Debian: 'apt-get install -y acl'; RHEL: 'yum install -y acl') and restart the build agent. "
                            + "Until then, the operator-triggered cache wipe and cleanup will report permission errors for root-owned files. Underlying cause: {}.",
                    label, cacheRoot, missingBinary.getMessage());
            return false;
        }
        try {
            boolean finished = process.waitFor(SETFACL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("setfacl on {} cache at {} did not finish within {}s; killed the process. The cache may have many files; consider running setfacl manually with a longer "
                        + "timeout: 'sudo setfacl -R -m u:<artemis-user>:rwx -d -m u:<artemis-user>:rwx {}'.", label, cacheRoot, SETFACL_TIMEOUT_SECONDS, cacheRoot);
                return false;
            }
            int exit = process.exitValue();
            if (exit != 0) {
                String output = new String(process.getInputStream().readAllBytes()).strip();
                log.warn(
                        "setfacl on {} cache at {} returned non-zero exit {} (output: {}). The filesystem may not support POSIX ACLs, or the JVM may not own the directory. "
                                + "Verify with 'getfacl {}' and re-run 'sudo setfacl -R -m u:<artemis-user>:rwx -d -m u:<artemis-user>:rwx {}' if needed.",
                        label, cacheRoot, exit, output, cacheRoot, cacheRoot);
                return false;
            }
            return true;
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            log.warn("Interrupted while waiting for setfacl on {} cache at {}; ACL not applied.", label, cacheRoot);
            return false;
        }
        catch (IOException readError) {
            log.warn("Could not read setfacl output for {} cache at {}: {}", label, cacheRoot, readError.getMessage());
            return false;
        }
    }

    /**
     * @return the OS user name the JVM is running as, or {@code null} if the system property is missing. Used as the
     *         ACL subject so the agent has rwx access independent of the build container's user identity.
     */
    private String currentUser() {
        String user = System.getProperty("user.name");
        return (user != null && !user.isBlank()) ? user : null;
    }

    /**
     * Build the {@code setfacl} argv as: {@code ["setfacl", ...args, cacheRoot]}. Extracted so a unit test can
     * assert the exact command without having {@code setfacl} on PATH. The path must be the LAST argument because
     * setfacl interprets the trailing positional as the file/directory to operate on; all flags and ACL specs
     * come before it. An earlier off-by-one bug here truncated the final ACL spec and produced
     * "Invalid argument near character 1" on every staging deployment until this method was extracted and tested.
     */
    static String[] buildSetfaclCommand(Path cacheRoot, String... args) {
        String[] full = new String[args.length + 2];
        full[0] = "setfacl";
        System.arraycopy(args, 0, full, 1, args.length);
        full[full.length - 1] = cacheRoot.toString();
        return full;
    }
}
