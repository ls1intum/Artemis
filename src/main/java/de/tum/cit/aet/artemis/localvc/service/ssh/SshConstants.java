package de.tum.cit.aet.artemis.localvc.service.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.apache.sshd.common.AttributeRepository;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;

@Profile(PROFILE_LOCALVC)
public class SshConstants {

    public static final AttributeRepository.AttributeKey<Boolean> IS_BUILD_AGENT_KEY = new AttributeRepository.AttributeKey<>();

    /**
     * The short name of the build agent whose public key authenticated this session.
     * <p>
     * Derived from the key match rather than claimed by the client, and it is what lets the repository check ask which
     * build jobs this particular agent is running instead of treating every agent as interchangeable.
     */
    public static final AttributeRepository.AttributeKey<String> BUILD_AGENT_NAME_KEY = new AttributeRepository.AttributeKey<>();

    public static final AttributeRepository.AttributeKey<User> USER_KEY = new AttributeRepository.AttributeKey<>();

    public static final AttributeRepository.AttributeKey<ProgrammingExercise> REPOSITORY_EXERCISE_KEY = new AttributeRepository.AttributeKey<>();

    public static final AttributeRepository.AttributeKey<VcsAccessLog> VCS_ACCESS_LOG_KEY = new AttributeRepository.AttributeKey<>();

    public static final AttributeRepository.AttributeKey<ProgrammingExerciseParticipation> PARTICIPATION_KEY = new AttributeRepository.AttributeKey<>();
}
