package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationSettingInfoDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationSettingSpecificationRequestDTO;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationSettingPresetTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationSettingSpecificationTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class UserCourseNotificationSettingResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "ucntest";

    @Autowired
    private JsonMapper objectMapper;

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

        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldApplyPresetWhenSetSettingPresetIsCalled() throws Exception {
        Short presetId = 2;

        var result = request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-preset", course.getId())
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
    void shouldApplySpecificationWhenSetSettingSpecificationIsCalled() throws Exception {
        Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels = new HashMap<>();
        Map<NotificationChannelOption, Boolean> channelSettings = new HashMap<>();
        channelSettings.put(NotificationChannelOption.EMAIL, true);
        channelSettings.put(NotificationChannelOption.WEBAPP, true);
        channelSettings.put(NotificationChannelOption.PUSH, false);

        notificationTypeChannels.put((short) 1, channelSettings);

        CourseNotificationSettingSpecificationRequestDTO requestDTO = new CourseNotificationSettingSpecificationRequestDTO(notificationTypeChannels);

        var result = request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-specification", course.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestDTO)));

        result.andExpect(status().isOk());

        var savedPreset = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(savedPreset).isNotNull();
        assertThat(savedPreset.getSettingPreset()).isEqualTo((short) 0);

        var savedSpecs = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(savedSpecs).isNotEmpty();

        var spec = savedSpecs.stream().filter(s -> s.courseNotificationType() == 1).findFirst();

        assertThat(spec).isPresent();
        assertThat(spec.get().email()).isTrue();
        assertThat(spec.get().webapp()).isTrue();
        assertThat(spec.get().push()).isFalse();
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

        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-specification", course.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))).andExpect(status().isOk());

        var presetBeforeChange = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(presetBeforeChange.getSettingPreset()).isEqualTo((short) 0);

        var specsBeforeChange = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(specsBeforeChange).isNotEmpty();

        Short presetId = 2;

        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-preset", course.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(presetId))).andExpect(status().isOk());

        // Hazelcast-backed @CacheEvict propagation can lag behind the database write inside a single
        // request. Clear the affected caches before re-reading to make the assertion deterministic.
        cacheManager.getCache("userCourseNotificationSettingPreset").clear();
        cacheManager.getCache("userCourseNotificationSettingSpecification").clear();

        var presetAfterChange = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(presetAfterChange.getSettingPreset()).isEqualTo(presetId);

        var specsAfterChange = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(user.getId(), course.getId());

        assertThat(specsAfterChange).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCorrectSettingInfoWhenGetSettingInfoIsCalled() throws Exception {
        Short presetId = 2;
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-preset", course.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(presetId))).andExpect(status().isOk());

        var result = request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/{courseId}/settings", course.getId()).contentType(MediaType.APPLICATION_JSON))
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

        var result = request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/{courseId}/settings", course.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        String responseContent = result.getResponse().getContentAsString();
        var responseDTO = objectMapper.readValue(responseContent, CourseNotificationSettingInfoDTO.class);

        assertThat(responseDTO).isNotNull();
        assertThat(responseDTO.notificationTypeChannels()).isNotNull();
        assertThat(responseDTO.selectedPreset()).isEqualTo((short) 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldPreserveCustomZeroFalseFlagsAndPartialNullOverrides() throws Exception {
        request.performMvcRequest(
                MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-preset", course.getId()).contentType(MediaType.APPLICATION_JSON).content("0"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        clearSettingCaches();
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-specification", course.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationTypeChannels\":{\"1\":{\"EMAIL\":true,\"PUSH\":true,\"WEBAPP\":true}}}")).andExpect(status().isOk()).andExpect(content().string(""));
        clearSettingCaches();
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-specification", course.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationTypeChannels\":{\"1\":{\"EMAIL\":false,\"PUSH\":null}}}")).andExpect(status().isOk()).andExpect(content().string(""));
        clearSettingCaches();
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/{courseId}/settings", course.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedPreset").value(0)).andExpect(jsonPath("$.notificationTypeChannels['1'].EMAIL").value(false))
                .andExpect(jsonPath("$.notificationTypeChannels['1'].PUSH").value(true)).andExpect(jsonPath("$.notificationTypeChannels['1'].WEBAPP").value(true));
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-specification", course.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationTypeChannels\":{}}")).andExpect(status().isOk()).andExpect(content().string(""));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSerializeAllFalseChannelFlagsWithoutDroppingTheMapEntry() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-specification", course.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationTypeChannels\":{\"1\":{\"EMAIL\":false,\"PUSH\":false,\"WEBAPP\":false}}}")).andExpect(status().isOk()).andExpect(content().string(""));
        clearSettingCaches();
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/{courseId}/settings", course.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedPreset").value(0)).andExpect(jsonPath("$.notificationTypeChannels['1'].EMAIL").value(false))
                .andExpect(jsonPath("$.notificationTypeChannels['1'].PUSH").value(false)).andExpect(jsonPath("$.notificationTypeChannels['1'].WEBAPP").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "99", "-1", "32768", "{}" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectInvalidOrNullScalarPresetBodies(String body) throws Exception {
        request.performMvcRequest(
                MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-preset", course.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldKeepSettingsScopedToCurrentUserAndCourse() throws Exception {
        var otherUser = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userCourseNotificationSettingPresetRepository.save(new UserCourseNotificationSettingPreset(otherUser, course, (short) 3));
        var otherCourse = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        request.performMvcRequest(
                MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-preset", course.getId()).contentType(MediaType.APPLICATION_JSON).content("2"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        clearSettingCaches();
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/{courseId}/settings", course.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedPreset").value(2));
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/{courseId}/settings", otherCourse.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedPreset").value(1));
        assertThat(userCourseNotificationSettingPresetRepository.findSettingPresetByUserIdAndCourseId(otherUser.getId(), course.getId())).isEqualTo((short) 3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDenyAllThreeOperationsToNonMembers() throws Exception {
        var foreignCourse = courseUtilService.createEnrolledCourse(TEST_PREFIX + "foreign");
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/{courseId}/settings", foreignCourse.getId())).andExpect(status().isForbidden());
        request.performMvcRequest(
                MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-preset", foreignCourse.getId()).contentType(MediaType.APPLICATION_JSON).content("3"))
                .andExpect(status().isForbidden());
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/{courseId}/setting-specification", foreignCourse.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"notificationTypeChannels\":{}}")).andExpect(status().isForbidden());
    }

    private void clearSettingCaches() {
        cacheManager.getCache("userCourseNotificationSettingPreset").clear();
        cacheManager.getCache("userCourseNotificationSettingSpecification").clear();
    }
}
