package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.LearnerProfileDTO;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.service.profile.LearnerProfileService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

class LearnerProfileServiceTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private LearnerProfileRepository learnerProfileRepository;

    @InjectMocks
    private LearnerProfileService learnerProfileService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createProfile_shouldCreateAndSaveProfile() {
        User user = new User();
        user.setId(1L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearnerProfile profile = learnerProfileService.createProfile(user);

        assertThat(profile).isNotNull();
        assertThat(profile.getUser()).isEqualTo(user);
        assertThat(user.getLearnerProfile()).isEqualTo(profile);
        verify(userRepository).save(user);
    }

    @Test
    void getOrCreateLearnerProfile_shouldReturnExistingProfile() {
        User user = new User();
        user.setId(2L);
        LearnerProfile existingProfile = new LearnerProfile();
        existingProfile.setUser(user);
        when(learnerProfileRepository.findByUser(user)).thenReturn(Optional.of(existingProfile));

        LearnerProfile result = learnerProfileService.getOrCreateLearnerProfile(user);
        assertThat(result).isEqualTo(existingProfile);
        verify(learnerProfileRepository).findByUser(user);
        verifyNoInteractions(userRepository);
    }

    @Test
    void getOrCreateLearnerProfile_shouldCreateProfileIfNotExists() {
        User user = new User();
        user.setId(3L);
        when(learnerProfileRepository.findByUser(user)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearnerProfile result = learnerProfileService.getOrCreateLearnerProfile(user);
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(user.getLearnerProfile()).isEqualTo(result);
        verify(userRepository).save(user);
    }

    @Test
    void learnerProfileDTO_of_shouldReturnNullForNullInput() {
        assertThat(LearnerProfileDTO.of(null)).isNull();
    }

    @Test
    void learnerProfileDTO_of_shouldClampValuesWithinRange() {
        LearnerProfile profile = new LearnerProfile();
        profile.setId(42L);
        profile.setFeedbackDetail(2);
        profile.setFeedbackFormality(1);

        LearnerProfileDTO dto = LearnerProfileDTO.of(profile);
        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.feedbackDetail()).isEqualTo(2);
        assertThat(dto.feedbackFormality()).isEqualTo(1);
    }

    @Test
    void learnerProfile_addAndRemoveCourseLearnerProfiles_shouldWork() {
        LearnerProfile learnerProfile = new LearnerProfile();
        CourseLearnerProfile clp1 = new CourseLearnerProfile();
        CourseLearnerProfile clp2 = new CourseLearnerProfile();
        // add single
        assertThat(learnerProfile.addCourseLearnerProfile(clp1)).isTrue();
        // add duplicate
        assertThat(learnerProfile.addCourseLearnerProfile(clp1)).isFalse();
        // add all
        java.util.Set<CourseLearnerProfile> set = new java.util.HashSet<>();
        set.add(clp2);
        assertThat(learnerProfile.addAllCourseLearnerProfiles(set)).isTrue();
        // addAll with already present
        assertThat(learnerProfile.addAllCourseLearnerProfiles(set)).isFalse();
        // remove
        assertThat(learnerProfile.removeCourseLearnerProfile(clp1)).isTrue();
        // remove non-existent
        assertThat(learnerProfile.removeCourseLearnerProfile(clp1)).isFalse();
    }

    @Test
    void courseLearnerProfile_getLearnerProfile_shouldReturnSetValue() {
        CourseLearnerProfile clp = new CourseLearnerProfile();
        LearnerProfile lp = new LearnerProfile();
        clp.setLearnerProfile(lp);
        assertThat(clp.getLearnerProfile()).isEqualTo(lp);
    }
}
