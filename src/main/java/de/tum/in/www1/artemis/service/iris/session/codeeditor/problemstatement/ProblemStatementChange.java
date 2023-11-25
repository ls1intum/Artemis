package de.tum.in.www1.artemis.service.iris.session.codeeditor.problemstatement;

import de.tum.in.www1.artemis.service.iris.session.codeeditor.IrisChangeException;

/**
 * A change that can be applied to the problem statement of an exercise.
 */
public interface ProblemStatementChange {

    String apply(String problemStatement) throws IrisChangeException;
}
