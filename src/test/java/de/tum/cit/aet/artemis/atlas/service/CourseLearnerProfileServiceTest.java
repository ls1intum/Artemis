package de.tum.cit.aet.artemis.atlas.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.service.profile.CourseLearnerProfileService;
import de.tum.cit.aet.artemis.atlas.service.profile.LearnerProfileService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

class CourseLearnerProfileServiceTest {

    @Mock
    private CourseLearnerProfileRepository courseLearnerProfileRepository;

    @Mock
    private LearnerProfileRepository learnerProfileRepository;

    // while it's not used directly, the mock is needed for the service to work properly, otherwise the test fails with database exceptions
    @SuppressWarnings("unused")
    @Mock
    private LearnerProfileService learnerProfileService;

    @InjectMocks
    private CourseLearnerProfileService courseLearnerProfileService;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void createCourseLearnerProfile_shouldSaveProfile() {
        Course course = new Course();
        course.setId(1L);
        User user = new User();
        user.setId(2L);
        LearnerProfile learnerProfile = new LearnerProfile();
        user.setLearnerProfile(learnerProfile);
        learnerProfile.setUser(user);
        when(learnerProfileRepository.findByUserElseThrow(user)).thenReturn(learnerProfile);
        when(courseLearnerProfileRepository.save(any(CourseLearnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        courseLearnerProfileService.createCourseLearnerProfile(course, user);
        verify(courseLearnerProfileRepository).save(any(CourseLearnerProfile.class));
    }

    @Test
    void createCourseLearnerProfiles_shouldSaveMultipleProfiles() {
        Course course = new Course();
        course.setId(1L);
        User user1 = new User();
        user1.setId(2L);
        user1.setLogin("user1");
        User user2 = new User();
        user2.setId(3L);
        user2.setLogin("user2");
        LearnerProfile lp1 = new LearnerProfile();
        LearnerProfile lp2 = new LearnerProfile();
        user1.setLearnerProfile(lp1);
        user2.setLearnerProfile(lp2);
        lp1.setUser(user1);
        lp2.setUser(user2);
        Set<User> users = new HashSet<>();
        users.add(user1);
        users.add(user2);
        when(learnerProfileRepository.findByUserElseThrow(user1)).thenReturn(lp1);
        when(learnerProfileRepository.findByUserElseThrow(user2)).thenReturn(lp2);
        when(learnerProfileRepository.findAllByUserIn(users)).thenReturn(Set.of(lp1, lp2));
        when(courseLearnerProfileRepository.findByLoginAndCourse(user1.getLogin(), course)).thenReturn(Optional.empty());
        when(courseLearnerProfileRepository.findByLoginAndCourse(user2.getLogin(), course)).thenReturn(Optional.empty());
        when(courseLearnerProfileRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<CourseLearnerProfile> iterable = invocation.getArgument(0);
            java.util.List<CourseLearnerProfile> list = new java.util.ArrayList<>();
            iterable.forEach(list::add);
            return list;
        });

        courseLearnerProfileService.createCourseLearnerProfiles(course, users);
        verify(courseLearnerProfileRepository).saveAll(any());
    }

    @Test
    void deleteCourseLearnerProfile_shouldDeleteProfile() {
        Course course = new Course();
        course.setId(1L);
        User user = new User();
        user.setId(2L);
        LearnerProfile learnerProfile = new LearnerProfile();
        user.setLearnerProfile(learnerProfile);
        learnerProfile.setUser(user);

        courseLearnerProfileService.deleteCourseLearnerProfile(course, user);
        verify(courseLearnerProfileRepository).deleteByCourseAndUser(course, user);
    }
}
