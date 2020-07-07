package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.io.File;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class TextExerciseExportService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseExportService.class);

    public TextExerciseExportService(
        TextExerciseRepository textExerciseRepository,
        SubmissionRepository submissionRepository) {

    }




    public File exportStudentSubmissions(Long exerciseId, List<StudentParticipation> participations, @Nullable ZonedDateTime lateSubmissionFilter) {

        // TODO filter late submissions
        List<TextSubmission> submissions = participations.stream()
            .map(Participation::findLatestSubmission) // TODO findLatestSubmissionBefore
            .filter(Optional::isPresent)
            .map((opt) -> (TextSubmission)opt.get())
            .collect(Collectors.toList());

        return null;

    }


}
