package de.tum.cit.aet.artemis.programming.service.localvc.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.apache.sshd.common.AttributeRepository;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;

@Profile(PROFILE_LOCALVC)
public class SshConstants {

    public static final AttributeRepository.AttributeKey<Boolean> IS_BUILD_AGENT_KEY = new AttributeRepository.AttributeKey<>();

    public static final AttributeRepository.AttributeKey<User> USER_KEY = new AttributeRepository.AttributeKey<>();

    public static final AttributeRepository.AttributeKey<ProgrammingExercise> REPOSITORY_EXERCISE_KEY = new AttributeRepository.AttributeKey<>();

    public static final AttributeRepository.AttributeKey<VcsAccessLog> VCS_ACCESS_LOG_KEY = new AttributeRepository.AttributeKey<>();

    public static final AttributeRepository.AttributeKey<ProgrammingExerciseParticipation> PARTICIPATION_KEY = new AttributeRepository.AttributeKey<>();
}
