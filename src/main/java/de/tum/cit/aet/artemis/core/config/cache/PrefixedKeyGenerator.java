package de.tum.cit.aet.artemis.core.config.cache;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.interceptor.KeyGenerator;

/**
 * Inlined replacement for {@code tech.jhipster.config.cache.PrefixedKeyGenerator}.
 * <p>
 * Generates cache keys prefixed with the git commit ID or build timestamp
 * to ensure cache invalidation across deployments.
 */
public class PrefixedKeyGenerator implements KeyGenerator {

    private final String prefix;

    public PrefixedKeyGenerator(GitProperties gitProperties, BuildProperties buildProperties) {
        this.prefix = generatePrefix(gitProperties, buildProperties);
    }

    private String generatePrefix(GitProperties gitProperties, BuildProperties buildProperties) {
        if (gitProperties != null && gitProperties.getShortCommitId() != null) {
            return gitProperties.getShortCommitId();
        }
        if (buildProperties != null && buildProperties.getTime() != null) {
            return DateTimeFormatter.ISO_INSTANT.format(buildProperties.getTime());
        }
        return RandomStringUtils.secure().nextAlphanumeric(12);
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        return new PrefixedSimpleKey(prefix, method.getName(), params);
    }

    static class PrefixedSimpleKey implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String prefix;

        private final String methodName;

        private final Object[] params;

        private final int hashCodeValue;

        PrefixedSimpleKey(String prefix, String methodName, Object... elements) {
            this.prefix = prefix;
            this.methodName = methodName;
            this.params = elements.clone();
            this.hashCodeValue = Objects.hash(prefix, methodName) * 31 + Arrays.deepHashCode(this.params);
        }

        @Override
        public boolean equals(Object other) {
            return (this == other) || (other instanceof PrefixedSimpleKey otherKey && this.prefix.equals(otherKey.prefix) && this.methodName.equals(otherKey.methodName)
                    && Arrays.deepEquals(this.params, otherKey.params));
        }

        @Override
        public int hashCode() {
            return this.hashCodeValue;
        }

        @Override
        public String toString() {
            return prefix + " " + getClass().getSimpleName() + " [" + methodName + "(" + Arrays.toString(params) + ")]";
        }
    }
}
