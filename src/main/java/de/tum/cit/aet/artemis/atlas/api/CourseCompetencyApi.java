package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

@Profile(PROFILE_CORE)
@Controller
public class CourseCompetencyApi extends AbstractAtlasApi {

    private final Optional<CourseCompetencyRepository> optionalCourseCompetencyRepository;

    private final Optional<CompetencyRelationRepository> optionalCompetencyRelationRepository;

    private final Optional<PrerequisiteRepository> optionalPrerequisitesRepository;

    private final Optional<CompetencyRepository> optionalCompetencyRepository;

    public CourseCompetencyApi(Environment environment, Optional<CourseCompetencyRepository> optionalCourseCompetencyRepository,
            Optional<CompetencyRelationRepository> optionalCompetencyRelationRepository, Optional<PrerequisiteRepository> optionalPrerequisitesRepository,
            Optional<CompetencyRepository> optionalCompetencyRepository) {
        super(environment);
        this.optionalCourseCompetencyRepository = optionalCourseCompetencyRepository;
        this.optionalCompetencyRelationRepository = optionalCompetencyRelationRepository;
        this.optionalPrerequisitesRepository = optionalPrerequisitesRepository;
        this.optionalCompetencyRepository = optionalCompetencyRepository;
    }

    public void findAndSetCompetenciesForCourse(Course course) {
        optionalCompetencyRepository.ifPresent(service -> course.setNumberOfCompetencies(service.countByCourse(course)));
        optionalPrerequisitesRepository.ifPresent(service -> course.setNumberOfPrerequisites(service.countByCourse(course)));
    }

    public void deleteCompetenciesOfCourse(Course course) {
        optionalCompetencyRelationRepository.ifPresent(service -> service.deleteAllByCourseId(course.getId()));
        optionalPrerequisitesRepository.ifPresent(service -> service.deleteAll(course.getPrerequisites()));
        optionalCompetencyRepository.ifPresent(service -> service.deleteAll(course.getCompetencies()));
    }

    /**
     * ToDo: Consider moving to service
     * Deletes the competencies associated with the given lecture unit.
     *
     * @param lectureUnit the lecture unit
     */
    public void deleteCompetencyOfLectureUnit(@NotNull LectureUnit lectureUnit) {
        if (optionalCourseCompetencyRepository.isEmpty()) {
            return;
        }

        CourseCompetencyRepository repository = optionalCourseCompetencyRepository.get();
        if (!(lectureUnit instanceof ExerciseUnit)) {
            // update associated competencies
            Set<CourseCompetency> competencies = lectureUnit.getCompetencies();
            repository.saveAll(competencies.stream().map(competency -> {
                CourseCompetency updatedCompetency = repository.findByIdWithLectureUnitsElseThrow(competency.getId());
                updatedCompetency.getLectureUnits().remove(lectureUnit);
                return updatedCompetency;
            }).toList());
        }
    }
}
