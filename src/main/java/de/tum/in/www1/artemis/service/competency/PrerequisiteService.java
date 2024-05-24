package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.PrerequisiteRepository;
import de.tum.in.www1.artemis.web.rest.dto.competency.PrerequisiteRequestDTO;

/**
 * Service for managing prerequisite competencies.
 */
@Profile(PROFILE_CORE)
@Service
public class PrerequisiteService {

    private final PrerequisiteRepository prerequisiteRepository;

    private final CourseRepository courseRepository;

    public PrerequisiteService(PrerequisiteRepository prerequisiteRepository, CourseRepository courseRepository) {
        this.prerequisiteRepository = prerequisiteRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Creates a new prerequisite with the given values in the given course
     *
     * @param prerequisiteValues the values of the prerequisite to create
     * @param courseId           the id of the course to create the prerequisite in
     * @return the created prerequisite
     */
    public Prerequisite createPrerequisite(PrerequisiteRequestDTO prerequisiteValues, long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);

        var prerequisiteToCreate = new Prerequisite(prerequisiteValues.title(), prerequisiteValues.description(), prerequisiteValues.softDueDate(),
                prerequisiteValues.masteryThreshold(), prerequisiteValues.taxonomy(), prerequisiteValues.optional());
        prerequisiteToCreate.setCourse(course);

        return prerequisiteRepository.save(prerequisiteToCreate);
    }

    /**
     * Updates an existing prerequisite with the given values if it is part of the given course
     *
     * @param prerequisiteValues the new prerequisite values
     * @param prerequisiteId     the id of the prerequisite to update
     * @param courseId           the id of the course the prerequisite is part of
     * @return the updated prerequisite
     */
    public Prerequisite updatePrerequisite(PrerequisiteRequestDTO prerequisiteValues, long prerequisiteId, long courseId) {
        var existingPrerequisite = prerequisiteRepository.findByIdAndCourseIdElseThrow(prerequisiteId, courseId);

        existingPrerequisite.setTitle(prerequisiteValues.title());
        existingPrerequisite.setDescription(prerequisiteValues.description());
        existingPrerequisite.setTaxonomy(prerequisiteValues.taxonomy());
        existingPrerequisite.setMasteryThreshold(prerequisiteValues.masteryThreshold());
        existingPrerequisite.setOptional(prerequisiteValues.optional());

        return prerequisiteRepository.save(existingPrerequisite);
    }

    /**
     * Deletes an existing prerequisite if it is part of the given course or throws an EntityNotFoundException
     *
     * @param prerequisiteId the id of the prerequisite to delete
     * @param courseId       the id of the course the prerequisite is part of
     */
    public void deletePrerequisite(long prerequisiteId, long courseId) {
        prerequisiteRepository.findByIdAndCourseIdElseThrow(prerequisiteId, courseId);
        prerequisiteRepository.deleteById(prerequisiteId);
    }
}
