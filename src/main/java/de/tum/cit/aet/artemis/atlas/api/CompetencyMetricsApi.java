package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.dto.CompetencyJolDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyProgressDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.MapEntryLongLong;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyMetricsRepository;

@Controller
public class CompetencyMetricsApi extends AbstractAtlasApi {

    private final CompetencyMetricsRepository competencyMetricsRepository;

    public CompetencyMetricsApi(CompetencyMetricsRepository competencyMetricsRepository) {
        this.competencyMetricsRepository = competencyMetricsRepository;
    }

    public Set<CompetencyInformationDTO> findAllCompetencyInformationByCourseId(long courseId) {
        return competencyMetricsRepository.findAllCompetencyInformationByCourseId(courseId);
    }

    public Set<MapEntryLongLong> findAllExerciseIdsByCompetencyIds(Set<Long> competencyIds) {
        return competencyMetricsRepository.findAllExerciseIdsByCompetencyIds(competencyIds);
    }

    public Set<MapEntryLongLong> findAllLectureUnitIdsByCompetencyIds(Set<Long> competencyIds) {
        return competencyMetricsRepository.findAllLectureUnitIdsByCompetencyIds(competencyIds);
    }

    public Set<CompetencyProgressDTO> findAllCompetencyProgressForUserByCompetencyIds(long userId, Set<Long> competencyIds) {
        return competencyMetricsRepository.findAllCompetencyProgressForUserByCompetencyIds(userId, competencyIds);
    }

    public Set<CompetencyJolDTO> findAllLatestCompetencyJolValuesForUserByCompetencyIds(long userId, Set<Long> competencyIds) {
        return competencyMetricsRepository.findAllLatestCompetencyJolValuesForUserByCompetencyIds(userId, competencyIds);
    }

    public Set<CompetencyJolDTO> findAllLatestCompetencyJolValuesForUserByCompetencyIdsExcludeJolIds(long userId, Set<Long> competencyIds, Set<Long> jolIdsToExclude) {
        return competencyMetricsRepository.findAllLatestCompetencyJolValuesForUserByCompetencyIdsExcludeJolIds(userId, competencyIds, jolIdsToExclude);
    }
}
