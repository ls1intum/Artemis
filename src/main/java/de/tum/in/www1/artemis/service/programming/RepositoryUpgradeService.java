package de.tum.in.www1.artemis.service.programming;

import java.io.IOException;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.errors.GitAPIException;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

public abstract class RepositoryUpgradeService {

    abstract void upgradeRepositories(ProgrammingExercise exercise) throws IOException, GitAPIException, InterruptedException, XmlPullParserException;
}
