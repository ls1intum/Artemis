package de.tum.in.www1.artemis.domain;

import javax.annotation.Nullable;

public record Commit(@Nullable String commitHash, @Nullable String authorName, @Nullable String message, @Nullable String authorEmail, @Nullable String branch) {

}
