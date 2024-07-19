package de.tum.in.www1.artemis.config.migration.entries;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.REsultDTO;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.competency.CompetencyJolRepository;
import de.tum.in.www1.artemis.repository.competency.JolDTO;

public class MigrationEntryComputeOldJOL extends MigrationEntry {

    private final CompetencyJolRepository competencyJolRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final ResultRepository resultRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    public MigrationEntryComputeOldJOL(CompetencyJolRepository competencyJolRepository, ExerciseRepository exerciseRepository, LectureUnitRepository lectureUnitRepository,
            ResultRepository resultRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository) {
        this.competencyJolRepository = competencyJolRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.resultRepository = resultRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
    }

    @Override
    public void execute() {
        List<JolDTO> jols = competencyJolRepository.findAllUserIds();
        for (JolDTO jol : jols) {
            CourseCompetency competency = jol.competency();
            Set<Exercise> exercises = exerciseRepository.findAllByCompetencyId(competency.getId()).stream()
                    .filter(exercise -> exercise.getReleaseDate() == null || exercise.getReleaseDate().isBefore(jol.time())).collect(Collectors.toSet());
            Set<REsultDTO> lastResults = exercises.stream().map(exercise -> {
                Set<REsultDTO> results = resultRepository.findAllByExerciseUserAndModificationDate(exercise.getId(), jol.userId(), jol.time().toInstant());

                return results.stream().max(Comparator.comparing(REsultDTO::lastModifiedDate)).orElse(null);
            }).filter(Objects::nonNull).collect(Collectors.toSet());

            Set<LectureUnit> lectureUnits = lectureUnitRepository.findAllByCompetencyId(competency.getId()).stream()
                    .filter(lectureUnit -> lectureUnit.getLecture().getStartDate() == null || lectureUnit.getLecture().getStartDate().isBefore(jol.time()))
                    .collect(Collectors.toSet());
            Set<Long> lectureUnitIds = lectureUnits.stream().map(LectureUnit::getId).collect(Collectors.toSet());
            int numberOfCompletedLectureUnits = lectureUnitCompletionRepository.countByLectureUnitIdsAndUserId(lectureUnitIds, jol.userId());

            double progress;
            if (exercises.size() + lectureUnits.size() != 0) {
                progress = 100.0 * (numberOfCompletedLectureUnits + lastResults.size()) / (exercises.size() + lectureUnits.size());
            }
            else {
                progress = 0.0;
            }
            double confidence = lastResults.stream().mapToDouble(REsultDTO::score).average().orElse(0.0);

            competencyJolRepository.updateJOL(jol.userId(), jol.competency(), jol.time(), progress, confidence);
        }
    }

    @Override
    public String author() {
        return "stoehrj";
    }

    @Override
    public String date() {
        return "20240718214500";
    }
}
