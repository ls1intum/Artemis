package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.QuizSubmission;
import de.tum.in.www1.exerciseapp.repository.QuizSubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuizSubmissionService {

    private static final Map<String, QuizSubmission> cachedSubmissions = new ConcurrentHashMap<>();

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    /**
     * generate the key for cached submissions using the username (for security reasons) and the submission id
     *
     * @param username     the username of the user who sent the submission
     * @param submissionId the id of the submission
     * @return the key used in the cachedSubmissions Map
     */
    private String keyForUsernameAndSubmissionId(String username, Long submissionId) {
        return username + "_&_" + submissionId;
    }

    /**
     * Get the cached submission for the given username and submission id and remove it from the hashmap
     *
     * @param username     the username of the user who sent the submission
     * @param submissionId the id of the submission
     * @return The QuizSubmission object (answers saved by user through websocket)
     */
    public QuizSubmission getAndRemoveCachedSubmission(String username, Long submissionId) {
        // get submission from hash map
        QuizSubmission cachedSubmission = cachedSubmissions.get(keyForUsernameAndSubmissionId(username, submissionId));
        if (cachedSubmission != null) {
            // remove from hash map
            cachedSubmissions.remove(keyForUsernameAndSubmissionId(username, submissionId));
        }
        // return submission
        return cachedSubmission;
    }

    /**
     * Get the cached submission for the given username and submission id
     *
     * @param username     the username of the user who sent the submission
     * @param submissionId the id of the submission
     * @return The QuizSubmission object (answers saved by user through websocket)
     */
    public QuizSubmission getCachedSubmission(String username, Long submissionId) {
        return cachedSubmissions.get(keyForUsernameAndSubmissionId(username, submissionId));
    }

    /**
     * Set the cached submission for the given username and submission id
     *
     * @param username       the username of the user who sent the submission
     * @param quizSubmission The QuizSubmission object (answers saved by user through websocket)
     */
    public void setCachedSubmission(String username, QuizSubmission quizSubmission) {
        cachedSubmissions.put(keyForUsernameAndSubmissionId(username, quizSubmission.getId()), quizSubmission);
    }

    /**
     * Remove the cached submission for the given username and submission id
     *
     * @param username       the username of the user who sent the submission
     * @param submissionId   The QuizSubmission object (answers saved by user through websocket)
     */
    public void removeCachedSubmission(String username, Long submissionId) {
        cachedSubmissions.remove(keyForUsernameAndSubmissionId(username, submissionId));
    }

    /**
     * Get the most up-to-date submission (during a quiz) for the given user and submissionId
     *
     * @param username     the username of the submission's owner
     * @param submissionId the submissionId
     * @return the submission entity
     */
    public QuizSubmission getActiveQuizSubmissionAndRemoveFromCache(String username, Long submissionId) {
        return Optional.ofNullable(getAndRemoveCachedSubmission(username, submissionId))
            .orElse(new QuizSubmission().submittedAnswers(new HashSet<>()));
    }

}
