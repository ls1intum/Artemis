package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.GuidedTourSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.user.UserUtilService;

class GuidedTourSettingResourceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gtsettingtest";

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    private Set<GuidedTourSetting> createGuidedTourSettings() {
        Set<GuidedTourSetting> guidedTourSettingSet = new HashSet<>();

        GuidedTourSetting guidedTourSetting1 = new GuidedTourSetting();
        guidedTourSetting1.setGuidedTourKey("course_overview_tour");
        guidedTourSetting1.setGuidedTourStep(5);
        guidedTourSetting1.setGuidedTourState(GuidedTourSetting.Status.FINISHED);

        GuidedTourSetting guidedTourSetting2 = new GuidedTourSetting();
        guidedTourSetting2.setGuidedTourKey("new_tour");
        guidedTourSetting2.setGuidedTourStep(7);
        guidedTourSetting2.setGuidedTourState(GuidedTourSetting.Status.STARTED);

        guidedTourSettingSet.add(guidedTourSetting1);
        guidedTourSettingSet.add(guidedTourSetting2);
        return guidedTourSettingSet;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void guidedTourSettingsIsInitiallyNull() throws Exception {
        User user = request.get("/api/public/account", HttpStatus.OK, User.class);
        assertThat(user.getGuidedTourSettings()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void updateGuidedTourSettings() throws Exception {
        Set<GuidedTourSetting> guidedTourSettingSet = this.createGuidedTourSettings();
        Set<?> serverGuidedTourSettings = request.putWithResponseBody("/api/guided-tour-settings", guidedTourSettingSet, Set.class, HttpStatus.OK);
        assertThat(serverGuidedTourSettings).hasSize(2);

        User user = request.get("/api/public/account", HttpStatus.OK, User.class);
        assertThat(user.getGuidedTourSettings()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void deleteGuidedTourSetting() throws Exception {
        Set<GuidedTourSetting> guidedTourSettingSet = this.createGuidedTourSettings();
        request.putWithResponseBody("/api/guided-tour-settings", guidedTourSettingSet, Set.class, HttpStatus.OK);
        request.delete("/api/guided-tour-settings/new_tour", HttpStatus.OK);

        User user = request.get("/api/public/account", HttpStatus.OK, User.class);
        assertThat(user.getGuidedTourSettings()).hasSize(1);
    }
}
