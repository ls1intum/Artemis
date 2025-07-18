package de.tum.cit.aet.artemis.core.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import io.jsonwebtoken.impl.DefaultClaimsBuilder;
import io.jsonwebtoken.impl.DefaultJwtParser;
import io.jsonwebtoken.impl.io.StandardCompressionAlgorithms;
import io.jsonwebtoken.impl.security.KeysBridge;
import io.jsonwebtoken.impl.security.StandardEncryptionAlgorithms;
import io.jsonwebtoken.impl.security.StandardKeyAlgorithms;
import io.jsonwebtoken.impl.security.StandardKeyOperations;
import io.jsonwebtoken.impl.security.StandardSecureDigestAlgorithms;
import io.jsonwebtoken.security.SignatureAlgorithm;

/**
 * Makes the JWT functionality of JJWT work with AOT and GraalVM native images.
 * taken from <a href="https://github.com/jwtk/jjwt/issues/637">Jjwt Issue</a>
 */
@Configuration
@ImportRuntimeHints(JjwtRuntimeHints.class)
class JjwtRuntimeHintsConfig {
}

class JjwtRuntimeHints implements RuntimeHintsRegistrar {

    private static final Class<?>[] JJWT_TYPES = { DefaultJwtParser.class, DefaultClaimsBuilder.class, StandardSecureDigestAlgorithms.class, StandardKeyOperations.class,
            SignatureAlgorithm.class, StandardEncryptionAlgorithms.class, StandardKeyAlgorithms.class, StandardCompressionAlgorithms.class, KeysBridge.class };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader cl) {
        for (Class<?> type : JJWT_TYPES) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.DECLARED_FIELDS);
        }
    }
}
