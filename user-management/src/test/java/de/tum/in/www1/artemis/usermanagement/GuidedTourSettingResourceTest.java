package de.tum.in.www1.artemis.usermanagement;

import de.tum.in.www1.artemis.domain.GuidedTourSetting;
import de.tum.in.www1.artemis.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class GuidedTourSettingResourceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 0, 0, 0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    public Set<GuidedTourSetting> createGuidedTourSettings() {
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
    @WithMockUser(value = "student1")
    public void guidedTourSettingsIsInitiallyNull() throws Exception {
        User user = request.get("/api/account", HttpStatus.OK, User.class);
        assertThat(user.getGuidedTourSettings().isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(value = "student1")
    public void updateGuidedTourSettings() throws Exception {
        Set<GuidedTourSetting> guidedTourSettingSet = this.createGuidedTourSettings();
        Set serverGuidedTourSettings = request.putWithResponseBody("/api/guided-tour-settings", guidedTourSettingSet, Set.class, HttpStatus.OK);
        assertThat(serverGuidedTourSettings).isNotNull();
        assertThat(serverGuidedTourSettings.isEmpty()).isFalse();
        assertThat(serverGuidedTourSettings.size()).isEqualTo(2);

        User user = request.get("/api/account", HttpStatus.OK, User.class);
        assertThat(user.getGuidedTourSettings()).isNotNull();
        assertThat(user.getGuidedTourSettings().isEmpty()).isFalse();
        assertThat(user.getGuidedTourSettings().size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(value = "student1")
    public void deleteGuidedTourSetting() throws Exception {
        Set<GuidedTourSetting> guidedTourSettingSet = this.createGuidedTourSettings();
        request.putWithResponseBody("/api/guided-tour-settings", guidedTourSettingSet, Set.class, HttpStatus.OK);
        request.delete("/api/guided-tour-settings/new_tour", HttpStatus.OK);

        User user = request.get("/api/account", HttpStatus.OK, User.class);
        assertThat(user.getGuidedTourSettings()).isNotNull();
        assertThat(user.getGuidedTourSettings().isEmpty()).isFalse();
        assertThat(user.getGuidedTourSettings().size()).isEqualTo(1);
    }
}
