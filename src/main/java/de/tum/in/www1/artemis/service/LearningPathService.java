package de.tum.in.www1.artemis.service;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.LearningPathRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class LearningPathService {

    private final Logger log = LoggerFactory.getLogger(LearningPathService.class);

    private final UserRepository userRepository;

    private final LearningPathRepository learningPathRepository;

    public LearningPathService(UserRepository userRepository, LearningPathRepository learningPathRepository) {
        this.userRepository = userRepository;
        this.learningPathRepository = learningPathRepository;
    }

    /**
     * Generate learning paths for all students enrolled in the course
     *
     * @param course course the learning paths are created for
     */
    public void generateLearningPaths(@NotNull Course course) {
        var students = userRepository.getStudents(course);
        students.forEach((student) -> {
            generateLearningPathForUser(course, student);
        });
        log.debug("Successfully created learning paths for all {} students in course (id={})", students.size(), course.getId());
    }

    /**
     * Generate learning path for the user in the course
     *
     * @param course course that defines the learning path
     * @param user   student for which the learning path is generated
     */
    public void generateLearningPathForUser(@NotNull Course course, @NotNull User user) {
        var existingLearningPaths = learningPathRepository.findByCourseIdAndUserId(course.getId(), user.getId());
        if (existingLearningPaths == null || existingLearningPaths.isEmpty()) {
            LearningPath lpToCreate = new LearningPath();
            lpToCreate.setUser(user);
            lpToCreate.setCourse(course);
            lpToCreate.setCompetencies(course.getCompetencies());
            var persistedLearningPath = learningPathRepository.save(lpToCreate);
            log.debug("Created LearningPath (id={}) for user (id={}) in course (id={})", persistedLearningPath.getId(), user.getId(), course.getId());
        }
    }

    /**
     * Search for all learning paths fitting a {@link PageableSearchDTO search query}. The result is paged.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @return A wrapper object containing a list of all found learning paths and the total number of pages
     */
    public SearchResultPageDTO<LearningPath> getAllOfCourseOnPageWithSize(final PageableSearchDTO<String> search, final Course course) {
        final var pageable = PageUtil.createLearningPathPageRequest(search);
        final var searchTerm = search.getSearchTerm();
        final Page<LearningPath> learningPathPage = learningPathRepository.findByLoginInCourse(searchTerm, course.getId(), pageable);
        return new SearchResultPageDTO<>(learningPathPage.getContent(), learningPathPage.getTotalPages());
    }
}
