package de.tum.cit.aet.artemis.programming.domain;

import org.jspecify.annotations.Nullable;

public record Commit(@Nullable String commitHash, @Nullable String authorName, @Nullable String message, @Nullable String authorEmail, @Nullable String branch) {

}
