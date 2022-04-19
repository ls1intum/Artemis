package de.tum.in.www1.artemis.service.hestia.behavioral;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;

public record BehavioralSolutionEntry(String filePath, ProgrammingExerciseTestCase testCase, int startLine, int lineCount) {
}
