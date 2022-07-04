package de.tum.in.www1.artemis.web.rest.repository.util;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.ResponseEntity;

public interface RepositoryExecutor<T> {

    ResponseEntity<T> exec() throws IOException, IllegalAccessException, GitAPIException, IllegalArgumentException;
}
