package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.repository.CourseCompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.PrerequisiteRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * Service for managing {@link Prerequisite} competencies.
 */
@Profile(PROFILE_CORE)
@Service
public class PrerequisiteService {

    private final PrerequisiteRepository prerequisiteRepository;

    private final CourseRepository courseRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public PrerequisiteService(PrerequisiteRepository prerequisiteRepository, CourseRepository courseRepository, CourseCompetencyRepository courseCompetencyRepository,
            UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.prerequisiteRepository = prerequisiteRepository;
        this.courseRepository = courseRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
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

    /**
     * Imports the courseCompetencies with the given ids as prerequisites into a course
     *
     * @param courseId            the course to import into
     * @param courseCompetencyIds the ids of the courseCompetencies to import
     * @return The list of imported prerequisites
     */
    public List<Prerequisite> importPrerequisites(long courseId, List<Long> courseCompetencyIds) {
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        List<CourseCompetency> courseCompetenciesToImport;
        if (authorizationCheckService.isAdmin(user)) {
            courseCompetenciesToImport = courseCompetencyRepository.findAllByIdElseThrow(courseCompetencyIds);
        }
        else {
            // only allow the user to import from courses where they are at least editor
            courseCompetenciesToImport = courseCompetencyRepository.findAllByIdAndUserIsAtLeastEditorInCourseElseThrow(courseCompetencyIds, user.getGroups());
        }

        var courseIds = courseCompetenciesToImport.stream().map(c -> c.getCourse().getId()).collect(Collectors.toSet());
        if (courseIds.contains(course.getId())) {
            throw new BadRequestException("You may not import a competency as prerequisite into the same course!");
        }

        var prerequisitesToImport = new ArrayList<Prerequisite>();
        for (var competency : courseCompetenciesToImport) {
            var prerequisiteToImport = new Prerequisite();
            prerequisiteToImport.setTitle(competency.getTitle());
            prerequisiteToImport.setDescription(competency.getDescription());
            prerequisiteToImport.setTaxonomy(competency.getTaxonomy());
            prerequisiteToImport.setOptional(competency.isOptional());
            prerequisiteToImport.setMasteryThreshold(competency.getMasteryThreshold());
            // do not set due date
            prerequisiteToImport.setLinkedCourseCompetency(competency);
            prerequisiteToImport.setCourse(course);

            prerequisitesToImport.add(prerequisiteToImport);
        }

        // TODO: link to learning paths once we support them for prerequisites.
        // TODO: import relations once we support them for prerequisites.

        return prerequisiteRepository.saveAll(prerequisitesToImport);
    }
}
