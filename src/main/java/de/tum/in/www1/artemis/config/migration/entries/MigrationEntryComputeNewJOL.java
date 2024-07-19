package de.tum.in.www1.artemis.config.migration.entries;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.REsultDTO;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.competency.CompetencyJolRepository;
import de.tum.in.www1.artemis.repository.competency.JolDTO;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;
import de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyExerciseMasteryCalculationDTO;

public class MigrationEntryComputeNewJOL extends MigrationEntry {

    private final CompetencyJolRepository competencyJolRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final ResultRepository resultRepository;

    private final SubmissionRepository submissionRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final CompetencyProgressService competencyProgressService;

    public MigrationEntryComputeNewJOL(CompetencyJolRepository competencyJolRepository, ExerciseRepository exerciseRepository, LectureUnitRepository lectureUnitRepository,
            ResultRepository resultRepository, SubmissionRepository submissionRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            CompetencyProgressService competencyProgressService) {
        this.competencyJolRepository = competencyJolRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.resultRepository = resultRepository;
        this.submissionRepository = submissionRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.competencyProgressService = competencyProgressService;
    }

    @Override
    public void execute() {
        List<JolDTO> jols = competencyJolRepository.findAllUserIds();
        for (JolDTO jol : jols) {
            CourseCompetency competency = jol.competency();
            Set<Exercise> exercises = exerciseRepository.findAllByCompetencyId(competency.getId()).stream()
                    .filter(exercise -> exercise.getReleaseDate() == null || exercise.getReleaseDate().isBefore(jol.time())).collect(Collectors.toSet());
            Set<CompetencyExerciseMasteryCalculationDTO> exerciseInfos = exercises.stream().map(exercise -> {
                Set<REsultDTO> results = resultRepository.findAllByExerciseUserAndModificationDate(exercise.getId(), jol.userId(), jol.time().toInstant());

                REsultDTO lastResult = results.stream().max(Comparator.comparing(REsultDTO::lastModifiedDate)).orElse(null);

                if (lastResult == null) {
                    return null;
                }

                Set<Long> resultIds = results.stream().map(REsultDTO::id).collect(Collectors.toSet());
                int numberOfSubmissions = submissionRepository.countByResultIds(resultIds);

                return new CompetencyExerciseMasteryCalculationDTO(exercise.getMaxPoints(), exercise.getDifficulty(), exercise instanceof ProgrammingExercise, lastResult.score(),
                        lastResult.score() * exercise.getMaxPoints() / 100, lastResult.lastModifiedDate(), numberOfSubmissions);
            }).filter(Objects::nonNull).collect(Collectors.toSet());

            Set<LectureUnit> lectureUnits = lectureUnitRepository.findAllByCompetencyId(competency.getId()).stream()
                    .filter(lectureUnit -> lectureUnit.getLecture().getStartDate() == null || lectureUnit.getLecture().getStartDate().isBefore(jol.time()))
                    .collect(Collectors.toSet());
            Set<Long> lectureUnitIds = lectureUnits.stream().map(LectureUnit::getId).collect(Collectors.toSet());
            int numberOfCompletedLectureUnits = lectureUnitCompletionRepository.countByLectureUnitIdsAndUserId(lectureUnitIds, jol.userId());

            CompetencyProgress competencyProgress = new CompetencyProgress();
            competencyProgressService.calculateProgress(lectureUnits, exerciseInfos, numberOfCompletedLectureUnits, competencyProgress);
            competencyProgressService.calculateConfidence(exerciseInfos, competencyProgress);

            competencyJolRepository.updateJOL(jol.userId(), jol.competency(), jol.time(), competencyProgress.getProgress(), competencyProgress.getConfidence());
        }
    }

    @Override
    public String author() {
        return "stoehrj";
    }

    @Override
    public String date() {
        return "20240718213000";
    }
}
