package de.tum.in.www1.artemis.config.migration.entries;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.competency.CompetencyJol;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.competency.CompetencyJolRepository;

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
        List<CompetencyJol> jols = competencyJolRepository.findAll();
        for (CompetencyJol jol : jols) {
            long userId = competencyJolRepository.findUserIdForJol(jol);

            CourseCompetency competency = jol.getCompetency();
            Set<Exercise> exercises = exerciseRepository.findAllByCompetencyId(competency.getId());
            Set<Result> lastResults = exercises.stream().map(exercise -> {
                Set<Result> results = resultRepository.findAllByExerciseUserAndModificationDate(exercise.getId(), userId, jol.getJudgementTime().toInstant());

                return results.stream().max(Comparator.comparing(Result::getLastModifiedDate)).orElse(null);
            }).collect(Collectors.toSet());

            Set<LectureUnit> lectureUnits = lectureUnitRepository.findAllByCompetencyId(competency.getId());
            Set<Long> lectureUnitIds = lectureUnits.stream().map(LectureUnit::getId).collect(Collectors.toSet());
            int numberOfCompletedLectureUnits = lectureUnitCompletionRepository.countByLectureUnitIdsAndUserId(lectureUnitIds, userId);

            double progress = 100.0 * (numberOfCompletedLectureUnits + lastResults.size()) / (exercises.size() + lectureUnits.size());
            double confidence = lastResults.stream().mapToDouble(Result::getScore).summaryStatistics().getAverage();

            jol.setCompetencyProgress(progress);
            jol.setCompetencyConfidence(confidence);
            competencyJolRepository.save(jol);
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
