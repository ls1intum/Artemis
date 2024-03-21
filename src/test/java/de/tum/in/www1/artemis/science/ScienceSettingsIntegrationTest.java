package de.tum.in.www1.artemis.science;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.science.ScienceSetting;
import de.tum.in.www1.artemis.repository.science.ScienceSettingRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class ScienceSettingsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "sciencesettingsintegration";

    @Autowired
    private ScienceSettingRepository scienceSettingRepository;

    @Autowired
    private UserUtilService userUtilService;

    private ScienceSetting settingA;

    private ScienceSetting settingB;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        settingA = new ScienceSetting(student1, "science.some-id", true);
        settingB = new ScienceSetting(student1, "science.some-other-id", false);
    }

    @AfterEach
    void tearDown() {
        scienceSettingRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetScienceSettingsForCurrentUser() throws Exception {
        scienceSettingRepository.save(settingA);
        scienceSettingRepository.save(settingB);

        List<ScienceSetting> scienceSettings = request.getList("/api/science-settings", HttpStatus.OK, ScienceSetting.class);

        assertThat(scienceSettings).as("scienceSetting A with recipient equal to current user is returned").contains(settingA);
        assertThat(scienceSettings).as("scienceSetting B with recipient equal to current user is returned").contains(settingB);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSaveScienceSettingsForCurrentUser_OK() throws Exception {
        ScienceSetting[] newlyChangedSettingsToSave = { settingA, settingB };

        ScienceSetting[] scienceSettingsResponse = request.putWithResponseBody("/api/science-settings", newlyChangedSettingsToSave, ScienceSetting[].class, HttpStatus.OK);

        boolean foundA = false;
        boolean foundB = false;
        for (ScienceSetting setting : scienceSettingsResponse) {
            if (setting.getSettingId().equals(settingA.getSettingId())) {
                foundA = true;
            }
            if (setting.getSettingId().equals(settingA.getSettingId())) {
                foundB = true;
            }
        }

        assertThat(foundA && foundB).as("Saved and received Science Settings A & B correctly").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSaveScienceSettingsForCurrentUser_BAD_REQUEST() throws Exception {
        request.putWithResponseBody("/api/science-settings", null, ScienceSetting[].class, HttpStatus.BAD_REQUEST);
    }
}
