package de.tum.in.www1.artemis.domain.participation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;

public interface ProgrammingExerciseParticipation extends ParticipationInterface {

    Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipation.class);

    String getRepositoryUrl();

    void setRepositoryUrl(String repositoryUrl);

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
    default String getUserIndependentRepositoryUrl() {
        if (getRepositoryUrl() == null) {
            return null;
        }
        try {
            URI repoUrl = new URI(getRepositoryUrl());
            // Note: the following line reconstructs the URL without using the authority, it removes ’username@' before the host
            return new URI(repoUrl.getScheme(), null, repoUrl.getHost(), repoUrl.getPort(), repoUrl.getPath(), null, null).toString();
        }
        catch (URISyntaxException e) {
            log.debug("Cannot create user independent repository url from {} due to malformed URL exception", getRepositoryUrl(), e);
            return null;
        }
    }

    /**
     * @return the repository url of the programming exercise participation wrapped in an object
     */
    @JsonIgnore
    default VcsRepositoryUrl getVcsRepositoryUrl() {
        var repoUrl = getRepositoryUrl();
        if (repoUrl == null) {
            return null;
        }

        try {
            return new VcsRepositoryUrl(repoUrl);
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
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
