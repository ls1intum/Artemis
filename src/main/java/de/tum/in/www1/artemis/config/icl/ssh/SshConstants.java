package de.tum.in.www1.artemis.config.icl.ssh;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import org.apache.sshd.common.AttributeRepository;
import org.springframework.context.annotation.Profile;

import de.tum.in.www1.artemis.domain.User;

@Profile(PROFILE_LOCALVC)
public class SshConstants {

    public static final AttributeRepository.AttributeKey<User> USER_KEY = new AttributeRepository.AttributeKey<>();
}
