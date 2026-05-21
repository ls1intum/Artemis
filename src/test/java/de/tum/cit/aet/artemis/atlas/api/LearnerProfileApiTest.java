package de.tum.cit.aet.artemis.atlas.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.service.profile.CourseLearnerProfileService;
import de.tum.cit.aet.artemis.atlas.service.profile.LearnerProfileService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

class LearnerProfileApiTest {

    @Mock
    private LearnerProfileService learnerProfileService;

    @Mock
    private CourseLearnerProfileService courseLearnerProfileService;

    @Mock
    private LearnerProfileRepository learnerProfileRepository;

    @Mock
    private CourseLearnerProfileRepository courseLearnerProfileRepository;

    @InjectMocks
    private LearnerProfileApi learnerProfileApi;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        learnerProfileApi = new LearnerProfileApi(learnerProfileService, courseLearnerProfileService, learnerProfileRepository, courseLearnerProfileRepository);
    }

    @Test
    void getOrCreateLearnerProfile_shouldDelegateToService() {
        User user = new User();
        LearnerProfile profile = new LearnerProfile();
        when(learnerProfileService.getOrCreateLearnerProfile(user)).thenReturn(profile);
        LearnerProfile result = learnerProfileApi.getOrCreateLearnerProfile(user);
        assertThat(result).isEqualTo(profile);
        verify(learnerProfileService).getOrCreateLearnerProfile(user);
    }

    @Test
    void createProfile_shouldDelegateToService() {
        User user = new User();
        learnerProfileApi.createProfile(user);
        verify(learnerProfileService).createProfile(user);
    }

    @Test
    void deleteProfile_shouldDelegateToRepository() {
        User user = new User();
        learnerProfileApi.deleteProfile(user);
        verify(learnerProfileRepository).deleteByUser(user);
    }

    @Test
    void createCourseLearnerProfile_shouldDelegateToService() {
        Course course = new Course();
        User user = new User();
        learnerProfileApi.createCourseLearnerProfile(course, user);
        verify(courseLearnerProfileService).createCourseLearnerProfile(course, user);
    }

    @Test
    void createCourseLearnerProfiles_shouldDelegateToService() {
        Course course = new Course();
        learnerProfileApi.createCourseLearnerProfiles(course, java.util.Set.of());
        verify(courseLearnerProfileService).createCourseLearnerProfiles(course, java.util.Set.of());
    }

    @Test
    void deleteCourseLearnerProfile_shouldDelegateToService() {
        Course course = new Course();
        User user = new User();
        learnerProfileApi.deleteCourseLearnerProfile(course, user);
        verify(courseLearnerProfileService).deleteCourseLearnerProfile(course, user);
    }

    @Test
    void deleteAllForCourse_shouldDelegateToService() {
        Course course = new Course();
        learnerProfileApi.deleteAllForCourse(course);
        verify(courseLearnerProfileService).deleteAllForCourse(course);
    }

    @Test
    void deleteAllForCourseId_shouldDelegateToRepository() {
        long courseId = 1L;
        learnerProfileApi.deleteAllForCourseId(courseId);
        verify(courseLearnerProfileRepository).deleteAllByCourseId(courseId);
    }
}
