package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.repository.CompetencyProgressRepository;
import de.tum.in.www1.artemis.repository.CourseCompetencyRepository;

/**
 * Service for managing competencies.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseCompetencyService {

    private final CompetencyProgressRepository competencyProgressRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CourseCompetencyService(CompetencyProgressRepository competencyProgressRepository, CourseCompetencyRepository courseCompetencyRepository) {
        this.competencyProgressRepository = competencyProgressRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    /**
     * Finds competencies within a course and fetch progress for the provided user.
     * <p>
     * As Spring Boot 3 doesn't support conditional JOIN FETCH statements, we have to retrieve the data manually.
     *
     * @param courseId The id of the course for which to fetch the competencies
     * @param userId   The id of the user for which to fetch the progress
     * @return The found competency
     */
    public List<CourseCompetency> findCourseCompetenciesWithProgressForUserByCourseId(Long courseId, Long userId) {
        List<CourseCompetency> competencies = courseCompetencyRepository.findByCourseIdOrderById(courseId);
        var progress = competencyProgressRepository.findByCompetenciesAndUser(competencies, userId).stream()
                .collect(Collectors.toMap(completion -> completion.getCompetency().getId(), completion -> completion));

        competencies.forEach(competency -> {
            if (progress.containsKey(competency.getId())) {
                competency.setUserProgress(Set.of(progress.get(competency.getId())));
            }
            else {
                competency.setUserProgress(Collections.emptySet());
            }
        });

        return competencies;
    }
}
