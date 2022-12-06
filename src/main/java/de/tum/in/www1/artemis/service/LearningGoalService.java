package de.tum.in.www1.artemis.service;

import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class LearningGoalService {

    private final LearningGoalRepository learningGoalRepository;

    private final AuthorizationCheckService authCheckService;

    public LearningGoalService(LearningGoalRepository learningGoalRepository, AuthorizationCheckService authCheckService) {
        this.learningGoalRepository = learningGoalRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Get all learning goals for a course, including the progress for the user.
     * @param course The course for which the learning goals should be retrieved.
     * @param user The user for whom to filter the visible lecture units attached to the learning goal.
     * @return A list of learning goals with their lecture units (filtered for the user).
     */
    public Set<LearningGoal> findAllForCourse(@NotNull Course course, @NotNull User user) {
        return learningGoalRepository.findAllForCourseWithProgressForUser(course.getId(), user.getId());
    }

    /**
     * Get all prerequisites for a course. Lecture units are removed if the student is not part of the course.
     * @param course The course for which the prerequisites should be retrieved.
     * @param user The user that is requesting the prerequisites.
     * @return A list of prerequisites (without lecture units if student is not part of course).
     */
    public Set<LearningGoal> findAllPrerequisitesForCourse(@NotNull Course course, @NotNull User user) {
        Set<LearningGoal> prerequisites = learningGoalRepository.findPrerequisitesByCourseId(course.getId());
        // Remove all lecture units
        for (LearningGoal prerequisite : prerequisites) {
            prerequisite.setLectureUnits(Collections.emptySet());
        }
        return prerequisites;
    }

    /**
     * Search for all learning goals fitting a {@link PageableSearchDTO search query}. The result is paged.
     * @param search The search query defining the search term and the size of the returned page
     * @param user   The user for whom to fetch all available lectures
     * @return A wrapper object containing a list of all found learning goals and the total number of pages
     */
    public SearchResultPageDTO<LearningGoal> getAllOnPageWithSize(final PageableSearchDTO<String> search, final User user) {
        final var pageable = PageUtil.createLearningGoalPageRequest(search);
        final var searchTerm = search.getSearchTerm();
        final Page<LearningGoal> lecturePage;
        if (authCheckService.isAdmin(user)) {
            lecturePage = learningGoalRepository.findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(searchTerm, searchTerm, pageable);
        }
        else {
            lecturePage = learningGoalRepository.findByTitleInLectureOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), pageable);
        }
        return new SearchResultPageDTO<>(lecturePage.getContent(), lecturePage.getTotalPages());
    }

}
