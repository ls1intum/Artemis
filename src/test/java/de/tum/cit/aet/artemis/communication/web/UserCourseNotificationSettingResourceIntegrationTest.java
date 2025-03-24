package de.tum.cit.aet.artemis.communication.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationSettingInfoDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationSettingSpecificationRequestDTO;
import de.tum.cit.aet.artemis.communication.test_repository.UserCourseNotificationSettingPresetTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.UserCourseNotificationSettingSpecificationTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class UserCourseNotificationSettingResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "ucntest";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserCourseNotificationSettingPresetTestRepository userCourseNotificationSettingPresetRepository;

    @Autowired
    private UserCourseNotificationSettingSpecificationTestRepository userCourseNotificationSettingSpecificationRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private CacheManager cacheManager;

    private User user;

    private Course course;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("userCourseNotificationSettingPreset").clear();
        cacheManager.getCache("userCourseNotificationSettingSpecification").clear();

        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        course = courseUtilService.createCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldApplyPresetWhenSetSettingPresetIsCalled() throws Exception {
        Short presetId = 2;

        var result = request.performMvcRequest(MockMvcRequestBuilders.put("/api/communication/notification/{courseId}/setting-preset", course.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(presetId)));

        result.andExpect(status().isOk());

        var savedPreset = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(savedPreset).isNotNull();
        assertThat(savedPreset.getSettingPreset()).isEqualTo(presetId);
        assertThat(savedPreset.getUser().getId()).isEqualTo(user.getId());
        assertThat(savedPreset.getCourse().getId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotApplyPresetWhenFeatureIsDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);
        Short presetId = 2;

        var result = request.performMvcRequest(MockMvcRequestBuilders.put("/api/communication/notification/{courseId}/setting-preset", course.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(presetId)));

        result.andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldApplySpecificationWhenSetSettingSpecificationIsCalled() throws Exception {
        Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels = new HashMap<>();
        Map<NotificationChannelOption, Boolean> channelSettings = new HashMap<>();
        channelSettings.put(NotificationChannelOption.EMAIL, true);
        channelSettings.put(NotificationChannelOption.WEBAPP, true);
        channelSettings.put(NotificationChannelOption.PUSH, false);

        notificationTypeChannels.put((short) 1, channelSettings);

        CourseNotificationSettingSpecificationRequestDTO requestDTO = new CourseNotificationSettingSpecificationRequestDTO(notificationTypeChannels);

        var result = request.performMvcRequest(MockMvcRequestBuilders.put("/api/communication/notification/{courseId}/setting-specification", course.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)));

        result.andExpect(status().isOk());

        var savedPreset = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(savedPreset).isNotNull();
        assertThat(savedPreset.getSettingPreset()).isEqualTo((short) 0);

        var savedSpecs = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(savedSpecs).isNotEmpty();

        var spec = savedSpecs.stream().filter(s -> s.getCourseNotificationType() == 1).findFirst();

        assertThat(spec).isPresent();
        assertThat(spec.get().isEmail()).isTrue();
        assertThat(spec.get().isWebapp()).isTrue();
        assertThat(spec.get().isPush()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotApplySpecificationWhenFeatureIsDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);

        Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels = new HashMap<>();
        Map<NotificationChannelOption, Boolean> channelSettings = new HashMap<>();
        channelSettings.put(NotificationChannelOption.EMAIL, true);
        channelSettings.put(NotificationChannelOption.WEBAPP, true);
        channelSettings.put(NotificationChannelOption.PUSH, false);

        notificationTypeChannels.put((short) 1, channelSettings);

        CourseNotificationSettingSpecificationRequestDTO requestDTO = new CourseNotificationSettingSpecificationRequestDTO(notificationTypeChannels);

        var result = request.performMvcRequest(MockMvcRequestBuilders.put("/api/communication/notification/{courseId}/setting-specification", course.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)));

        result.andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldChangeFromCustomToPresetWhenApplyPresetAfterCustomization() throws Exception {
        Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels = new HashMap<>();
        Map<NotificationChannelOption, Boolean> channelSettings = new HashMap<>();
        channelSettings.put(NotificationChannelOption.EMAIL, true);
        channelSettings.put(NotificationChannelOption.WEBAPP, true);
        channelSettings.put(NotificationChannelOption.PUSH, false);

        notificationTypeChannels.put((short) 1, channelSettings);

        CourseNotificationSettingSpecificationRequestDTO requestDTO = new CourseNotificationSettingSpecificationRequestDTO(notificationTypeChannels);

        request.performMvcRequest(MockMvcRequestBuilders.put("/api/communication/notification/{courseId}/setting-specification", course.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO))).andExpect(status().isOk());

        var presetBeforeChange = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(presetBeforeChange.getSettingPreset()).isEqualTo((short) 0);

        var specsBeforeChange = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(specsBeforeChange).isNotEmpty();

        Short presetId = 2;

        request.performMvcRequest(MockMvcRequestBuilders.put("/api/communication/notification/{courseId}/setting-preset", course.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(presetId))).andExpect(status().isOk());

        var presetAfterChange = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(presetAfterChange.getSettingPreset()).isEqualTo(presetId);

        var specsAfterChange = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(specsAfterChange).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCorrectSettingInfoWhenGetSettingInfoIsCalled() throws Exception {
        Short presetId = 2;
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/communication/notification/{courseId}/setting-preset", course.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(presetId))).andExpect(status().isOk());

        var result = request
                .performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/{courseId}/settings", course.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        String responseContent = result.getResponse().getContentAsString();
        var responseDTO = objectMapper.readValue(responseContent, CourseNotificationSettingInfoDTO.class);

        assertThat(responseDTO).isNotNull();
        assertThat(responseDTO.selectedPreset()).isEqualTo(presetId);
        assertThat(responseDTO.notificationTypeChannels()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnDefaultSettingInfoWhenNoPresetExists() throws Exception {
        userCourseNotificationSettingPresetRepository.deleteAll();

        var result = request
                .performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/{courseId}/settings", course.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        String responseContent = result.getResponse().getContentAsString();
        var responseDTO = objectMapper.readValue(responseContent, CourseNotificationSettingInfoDTO.class);

        assertThat(responseDTO).isNotNull();
        assertThat(responseDTO.notificationTypeChannels()).isNotNull();
        assertThat(responseDTO.selectedPreset()).isEqualTo((short) 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotReturnSettingInfoWhenFeatureIsDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/{courseId}/settings", course.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
