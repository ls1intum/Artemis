package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.repository.CompetencyRelationRepository;
import de.tum.in.www1.artemis.repository.CourseCompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.PrerequisiteRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.learningpath.LearningPathService;
import de.tum.in.www1.artemis.web.rest.dto.competency.PrerequisiteRequestDTO;

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

    private final LearningPathService learningPathService;

    private final CompetencyRelationRepository competencyRelationRepository;

    public PrerequisiteService(PrerequisiteRepository prerequisiteRepository, CourseRepository courseRepository, CourseCompetencyRepository courseCompetencyRepository,
            UserRepository userRepository, AuthorizationCheckService authorizationCheckService, LearningPathService learningPathService,
            CompetencyRelationRepository competencyRelationRepository) {
        this.prerequisiteRepository = prerequisiteRepository;
        this.courseRepository = courseRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.learningPathService = learningPathService;
        this.competencyRelationRepository = competencyRelationRepository;
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

        var persistedPrerequisite = prerequisiteRepository.save(prerequisiteToCreate);

        if (course.getLearningPathsEnabled()) {
            learningPathService.linkCompetencyToLearningPathsOfCourse(persistedPrerequisite, course.getId());
        }

        return persistedPrerequisite;
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
        existingPrerequisite.setSoftDueDate(prerequisiteValues.softDueDate());
        existingPrerequisite.setMasteryThreshold(prerequisiteValues.masteryThreshold());
        existingPrerequisite.setOptional(prerequisiteValues.optional());

        return prerequisiteRepository.save(existingPrerequisite);
    }

    /**
     * Imports the courseCompetencies with the given ids as prerequisites into a course
     *
     * @param courseId            the course to import into
     * @param courseCompetencyIds the ids of the courseCompetencies to import
     * @param relations           the relations between the prerequisites to import
     * @return The list of imported prerequisites
     */
    public List<Prerequisite> importPrerequisites(long courseId, List<Long> courseCompetencyIds, Set<CompetencyRelation> relations) {
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        List<CourseCompetency> courseCompetenciesToImport;
        var idToImportedCompetency = new HashMap<Long, Prerequisite>();
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

            idToImportedCompetency.put(competency.getId(), prerequisiteRepository.save(prerequisiteToImport));
        }

        if (course.getLearningPathsEnabled()) {
            learningPathService.linkCompetenciesToLearningPathsOfCourse(idToImportedCompetency.values().stream().toList(), course.getId());
        }

        if (!relations.isEmpty()) {
            for (var relation : relations) {
                var tailCompetency = idToImportedCompetency.get(relation.getTailCompetency().getId());
                var headCompetency = idToImportedCompetency.get(relation.getHeadCompetency().getId());

                CompetencyRelation relationToImport = new CompetencyRelation();
                relationToImport.setType(relation.getType());
                relationToImport.setTailCompetency(tailCompetency);
                relationToImport.setHeadCompetency(headCompetency);

                competencyRelationRepository.save(relationToImport);
            }
        }

        return idToImportedCompetency.values().stream().toList();
    }
}
