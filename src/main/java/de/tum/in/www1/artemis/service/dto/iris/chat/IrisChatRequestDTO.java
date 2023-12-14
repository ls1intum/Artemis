package de.tum.in.www1.artemis.service.dto.iris.chat;

import java.util.List;
import java.util.Map;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;

// @formatter:off
public record IrisChatRequestDTO(
        ProgrammingExercise exercise,
        Course course,
        ProgrammingSubmission latestSubmission,
        boolean buildFailed,
        List<BuildLogEntry> buildLog,
        IrisChatSession session,
        String gitDiff,
        Map<String, String> templateRepository,
        Map<String, String> studentRepository
) {}
// @formatter:on
