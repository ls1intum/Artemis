package de.tum.cit.aet.artemis.programming.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.participation.ParticipationInterface;

public interface ProgrammingExerciseParticipation extends ParticipationInterface {

    Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipation.class);

    String getRepositoryUri();

    void setRepositoryUri(String repositoryUri);

    String getBuildPlanId();

    void setBuildPlanId(String buildPlanId);

    ProgrammingExercise getProgrammingExercise();

    void setProgrammingExercise(ProgrammingExercise programmingExercise);

    Set<Result> getResults();

    /**
     * This method is used to automatically create a user independent URL when serializing subclasses into json
     *
     * @return a user independent url without the username
     */
    @Nullable
    @JsonInclude
    default String getUserIndependentRepositoryUri() {
        if (getRepositoryUri() == null) {
            return null;
        }
        try {
            URI repoUri = new URI(getRepositoryUri());
            // Note: the following line reconstructs the URL without using the authority, it removes ’username@' before the host
            return new URI(repoUri.getScheme(), null, repoUri.getHost(), repoUri.getPort(), repoUri.getPath(), null, null).toString();
        }
        catch (URISyntaxException e) {
            log.debug("Cannot create user independent repository uri from {} due to malformed URL exception", getRepositoryUri(), e);
            return null;
        }
    }

    /**
     * @return the repository uri of the programming exercise participation wrapped in an object
     */
    @JsonIgnore
    default VcsRepositoryUri getVcsRepositoryUri() {
        var repoUri = getRepositoryUri();
        if (repoUri == null) {
            return null;
        }

        try {
            return new VcsRepositoryUri(repoUri);
        }
        catch (URISyntaxException e) {
            log.warn("Cannot create URI for repositoryUri: {} due to the following error: {}", repoUri, e.getMessage());
        }
        return null;
    }

    /**
     * Check if the participation is locked.
     * This is the case when the participation is a ProgrammingExerciseStudentParticipation,
     * and the student can't make any changes to their repository anymore.
     * While we can control this easily in the remote VCS, we need to check this manually
     * for the local repository on the Artemis server.
     *
     * @return true if repository is locked, false if not.
     */
    @JsonIgnore
    default boolean isLocked() {
        if (!(this instanceof ProgrammingExerciseStudentParticipation studentParticipation)) {
            return false;
        }

        return studentParticipation.isLocked();
    }
}
