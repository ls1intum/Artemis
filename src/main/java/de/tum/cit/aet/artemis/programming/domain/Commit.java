package de.tum.cit.aet.artemis.programming.domain;

import jakarta.annotation.Nullable;

public record Commit(@Nullable String commitHash, @Nullable String authorName, @Nullable String message, @Nullable String authorEmail, @Nullable String branch) {

}
