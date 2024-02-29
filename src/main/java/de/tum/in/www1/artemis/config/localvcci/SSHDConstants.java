package de.tum.in.www1.artemis.config.localvcci;

import org.apache.sshd.common.AttributeRepository;

import de.tum.in.www1.artemis.domain.User;

public class SSHDConstants {

    public static final AttributeRepository.AttributeKey<User> USER_KEY = new AttributeRepository.AttributeKey<>();
}
