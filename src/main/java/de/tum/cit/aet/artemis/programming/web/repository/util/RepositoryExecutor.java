package de.tum.cit.aet.artemis.programming.web.repository.util;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.ResponseEntity;

public interface RepositoryExecutor<T> {

    ResponseEntity<T> exec() throws IOException, GitAPIException, IllegalArgumentException;
}
