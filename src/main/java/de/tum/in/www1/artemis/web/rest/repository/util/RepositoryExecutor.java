package de.tum.in.www1.artemis.web.rest.repository.util;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.ResponseEntity;

public interface RepositoryExecutor {

    ResponseEntity exec() throws IOException, InterruptedException, IllegalAccessException, GitAPIException, IllegalArgumentException;
}
