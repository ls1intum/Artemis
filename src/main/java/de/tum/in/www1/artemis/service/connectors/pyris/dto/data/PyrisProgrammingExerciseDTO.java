package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.LocalDateTime;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

public record PyrisProgrammingExerciseDTO(int id, String name, ProgrammingLanguage programmingLanguage, String templateRepositoryCommitHash, String solutionRepositoryCommitHash,
        String testsRepositoryCommitHash, String problemStatement, LocalDateTime startDate, LocalDateTime endDate, boolean isPracticeModeEnabled) {
}
