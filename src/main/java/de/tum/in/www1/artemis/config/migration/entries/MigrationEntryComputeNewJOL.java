package de.tum.in.www1.artemis.config.migration.entries;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.competency.CompetencyJol;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.competency.CompetencyJolRepository;
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
        List<CompetencyJol> jols = competencyJolRepository.findAll();
        for (CompetencyJol jol : jols) {
            long userId = competencyJolRepository.findUserIdForJol(jol);

            CourseCompetency competency = jol.getCompetency();
            Set<Exercise> exercises = exerciseRepository.findAllByCompetencyId(competency.getId());
            Set<CompetencyExerciseMasteryCalculationDTO> exerciseInfos = exercises.stream().map(exercise -> {
                Set<Result> results = resultRepository.findAllByExerciseUserAndModificationDate(exercise.getId(), userId, jol.getJudgementTime().toInstant());

                Result lastResult = results.stream().max(Comparator.comparing(Result::getLastModifiedDate)).orElse(null);

                Set<Long> resultIds = results.stream().map(Result::getId).collect(Collectors.toSet());
                int numberOfSubmissions = submissionRepository.countByResultIds(resultIds);

                return new CompetencyExerciseMasteryCalculationDTO(exercise.getMaxPoints(), exercise.getDifficulty(), exercise instanceof ProgrammingExercise,
                        lastResult.getScore(), lastResult.getScore() * exercise.getMaxPoints() / 100, lastResult.getLastModifiedDate(), numberOfSubmissions);
            }).collect(Collectors.toSet());

            Set<LectureUnit> lectureUnits = lectureUnitRepository.findAllByCompetencyId(competency.getId());
            Set<Long> lectureUnitIds = lectureUnits.stream().map(LectureUnit::getId).collect(Collectors.toSet());
            int numberOfCompletedLectureUnits = lectureUnitCompletionRepository.countByLectureUnitIdsAndUserId(lectureUnitIds, userId);

            CompetencyProgress competencyProgress = new CompetencyProgress();
            competencyProgressService.calculateProgress(lectureUnits, exerciseInfos, numberOfCompletedLectureUnits, competencyProgress);
            competencyProgressService.calculateConfidence(exerciseInfos, competencyProgress);

            jol.setCompetencyProgress(competencyProgress.getProgress());
            jol.setCompetencyConfidence(competencyProgress.getConfidence());
            competencyJolRepository.save(jol);
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
