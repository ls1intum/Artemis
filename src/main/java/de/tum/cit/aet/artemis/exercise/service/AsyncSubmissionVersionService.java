package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;

/**
 * Writes submission versions off the request thread.
 * <p>
 * This is a separate bean rather than an {@code @Async} method on {@link SubmissionVersionService} because Spring
 * implements {@code @Async} with a proxy: a call from one method of a bean to another method of the same bean does not
 * pass through that proxy and would silently run synchronously.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AsyncSubmissionVersionService {

    private static final Logger log = LoggerFactory.getLogger(AsyncSubmissionVersionService.class);

    private final SubmissionVersionRepository submissionVersionRepository;

    private final SubmissionRepository submissionRepository;

    private final UserRepository userRepository;

    public AsyncSubmissionVersionService(SubmissionVersionRepository submissionVersionRepository, SubmissionRepository submissionRepository, UserRepository userRepository) {
        this.submissionVersionRepository = submissionVersionRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Persists one submission version.
     * <p>
     * Takes identifiers and an already serialized content string rather than the entities themselves, so nothing loaded
     * by the request's session is touched here. The two references are re-read on this thread, which costs two lookups
     * by primary key and keeps the write correct regardless of what the request thread does next.
     * <p>
     * A failure is logged rather than propagated. There is no caller left to return it to, and losing a version must
     * not be able to affect the submission it describes, which is already saved by this point.
     *
     * @param submissionId the submission the version belongs to
     * @param authorId     the user who authored the submission update
     * @param content      the serialized submission content
     */
    @Async("submissionVersionExecutor")
    public void write(long submissionId, long authorId, String content) {
        try {
            Submission submission = submissionRepository.findById(submissionId).orElse(null);
            if (submission == null) {
                // The submission can legitimately be gone by now, for instance if the exercise or participation was
                // deleted between the request finishing and this running.
                log.debug("Not writing a submission version for submission {}: it no longer exists", submissionId);
                return;
            }
            User author = userRepository.findById(authorId).orElse(null);
            if (author == null) {
                log.debug("Not writing a submission version for submission {}: author {} no longer exists", submissionId, authorId);
                return;
            }
            SubmissionVersion version = new SubmissionVersion();
            version.setSubmission(submission);
            version.setAuthor(author);
            version.setContent(content);
            submissionVersionRepository.save(version);
        }
        catch (Exception e) {
            log.error("Could not write the submission version for submission {}", submissionId, e);
        }
    }
}
