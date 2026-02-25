package de.tum.cit.aet.artemis.atlas.science;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceSetting;
import de.tum.cit.aet.artemis.atlas.dto.ScienceSettingDTO;
import de.tum.cit.aet.artemis.core.domain.User;

class ScienceSettingsIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "sciencesettingsintegration";

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

        List<ScienceSettingDTO> scienceSettings = request.getList("/api/atlas/science-settings", HttpStatus.OK, ScienceSettingDTO.class);

        assertThat(scienceSettings).as("scienceSetting A with recipient equal to current user is returned").contains(ScienceSettingDTO.of(settingA));
        assertThat(scienceSettings).as("scienceSetting B with recipient equal to current user is returned").contains(ScienceSettingDTO.of(settingB));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSaveScienceSettingsForCurrentUser_OK() throws Exception {
        ScienceSettingDTO[] newlyChangedSettingsToSave = { ScienceSettingDTO.of(settingA), ScienceSettingDTO.of(settingB) };

        ScienceSettingDTO[] scienceSettingsResponse = request.putWithResponseBody("/api/atlas/science-settings", newlyChangedSettingsToSave, ScienceSettingDTO[].class,
                HttpStatus.OK);

        boolean foundA = false;
        boolean foundB = false;
        for (ScienceSettingDTO setting : scienceSettingsResponse) {
            if (setting.settingId().equals(settingA.getSettingId())) {
                foundA = true;
            }
            if (setting.settingId().equals(settingB.getSettingId())) {
                foundB = true;
            }
        }

        assertThat(foundA && foundB).as("Saved and received Science Settings A & B correctly").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSaveScienceSettingsForCurrentUser_BAD_REQUEST() throws Exception {
        request.putWithResponseBody("/api/atlas/science-settings", null, ScienceSettingDTO[].class, HttpStatus.BAD_REQUEST);
    }
}
