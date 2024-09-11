package de.tum.cit.aet.artemis.programming.icl.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.apache.sshd.common.AttributeRepository;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.domain.User;

@Profile(PROFILE_LOCALVC)
public class SshConstants {

    public static final AttributeRepository.AttributeKey<Boolean> IS_BUILD_AGENT_KEY = new AttributeRepository.AttributeKey<>();

    public static final AttributeRepository.AttributeKey<User> USER_KEY = new AttributeRepository.AttributeKey<>();
}
