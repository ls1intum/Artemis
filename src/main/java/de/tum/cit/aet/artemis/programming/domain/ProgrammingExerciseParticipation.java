package de.tum.cit.aet.artemis.programming.domain;

import java.net.URI;
import java.net.URISyntaxException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.exercise.domain.participation.ParticipationInterface;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

public interface ProgrammingExerciseParticipation extends ParticipationInterface {

    Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipation.class);

    String getRepositoryUri();

    void setRepositoryUri(String repositoryUri);

    String getBuildPlanId();

    void setBuildPlanId(String buildPlanId);

    ProgrammingExercise getProgrammingExercise();

    void setProgrammingExercise(ProgrammingExercise programmingExercise);

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
    default LocalVCRepositoryUri getVcsRepositoryUri() {
        var repoUri = getRepositoryUri();
        if (repoUri == null) {
            return null;
        }

        try {
            return new LocalVCRepositoryUri(repoUri);
        }
        catch (LocalVCInternalException e) {
            log.warn("Cannot create URI for repositoryUri: {} due to the following error: {}", repoUri, e.getMessage());
        }
        return null;
    }
}
