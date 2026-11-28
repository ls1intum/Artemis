package de.tum.cit.aet.artemis.sandbox.domain;

import org.jspecify.annotations.Nullable;

/**
 * The build files at the root of the tests repository, as far as they exist.
 *
 * @param gradleBuild content of {@code build.gradle}
 * @param mavenPom    content of {@code pom.xml}
 */
public record BuildFiles(@Nullable String gradleBuild, @Nullable String mavenPom) {
}
