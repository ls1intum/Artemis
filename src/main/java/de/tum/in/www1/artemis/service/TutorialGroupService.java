package de.tum.in.www1.artemis.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TutorialGroup;
import de.tum.in.www1.artemis.repository.TutorialGroupRepository;

@Service
public class TutorialGroupService {

    private final TutorialGroupRepository tutorialGroupRepository;

    public TutorialGroupService(TutorialGroupRepository tutorialGroupRepository) {
        this.tutorialGroupRepository = tutorialGroupRepository;
    }

    public Set<TutorialGroup> findAllForCourse(Course course) {
        return tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantAndRegisteredStudents(course.getId());
    }

}
