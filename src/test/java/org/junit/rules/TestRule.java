package org.junit.rules;

// workaround to avoid issues when excluding junit 4 and testcontainers https://github.com/testcontainers/testcontainers-java/issues/970#issuecomment-625044008
// after this issue is resolved we can remove this
@SuppressWarnings("unused")
public interface TestRule {
}
