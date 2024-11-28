package de.tum.cit.aet.artemis.atlas.repository.simple;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.core.repository.base.AbstractSimpleService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

@Profile(PROFILE_CORE)
@Service
public class CourseCompetencySimpleService extends AbstractSimpleService<CourseCompetency> {

    private static final String ENTITY_NAME = "CourseCompetency";

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CourseCompetencySimpleService(CourseCompetencyRepository courseCompetencyRepository) {
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    public CourseCompetency findByIdWithExercisesAndLectureUnitsAndLecturesElseThrow(long id) {
        return getValueElseThrow(courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(id), ENTITY_NAME);
    }

    public CourseCompetency findByIdWithExercisesAndLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(courseCompetencyRepository.findByIdWithExercisesAndLectureUnits(competencyId), ENTITY_NAME);
    }

    public CourseCompetency findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long competencyId) {
        return getValueElseThrow(courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsBidirectional(competencyId), ENTITY_NAME);
    }

    /**
     * Finds the set of ids of course competencies that are linked to a given learning object
     *
     * @param learningObject the learning object to find the course competencies for
     * @return the set of ids of course competencies linked to the learning object
     */
    public Set<Long> findAllIdsByLearningObject(LearningObject learningObject) {
        return switch (learningObject) {
            case LectureUnit lectureUnit -> courseCompetencyRepository.findAllIdsByLectureUnit(lectureUnit);
            case Exercise exercise -> courseCompetencyRepository.findAllIdsByExercise(exercise);
            default -> throw new IllegalArgumentException("Unknown LearningObject type: " + learningObject.getClass());
        };
    }

    public CourseCompetency findByIdWithExercisesElseThrow(long competencyId) {
        return getValueElseThrow(courseCompetencyRepository.findByIdWithExercises(competencyId), ENTITY_NAME);
    }

    public CourseCompetency findByIdWithLectureUnitsAndExercisesElseThrow(long competencyId) {
        return getValueElseThrow(courseCompetencyRepository.findByIdWithLectureUnitsAndExercises(competencyId), ENTITY_NAME);
    }
}
