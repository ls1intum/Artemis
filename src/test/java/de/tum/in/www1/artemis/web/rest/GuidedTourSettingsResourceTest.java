package de.tum.in.www1.artemis.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.GuidedTourSettings;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class GuidedTourSettingsResourceTest {

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @BeforeEach
    public void initTestCase() {
        database.resetDatabase();
        database.addUsers(1, 0, 0);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getGuidedTourSettings() throws Exception {
        List<GuidedTourSettings> guidedTourSettings = request.get("/api/guided-tour-settings", HttpStatus.OK, List.class);
    }

    @Test
    @WithMockUser(value = "student1")
    public void updateGuidedTourSettings() throws Exception {
        List<GuidedTourSettings> guidedTourSettingsList = new ArrayList<>();
        GuidedTourSettings guidedTourSettings = new GuidedTourSettings();
        guidedTourSettings.setGuidedTourKey("course_overview_tour");
        guidedTourSettings.setGuidedTourStep(5);

        guidedTourSettingsList.add(guidedTourSettings);
        request.putWithResponseBody("/api/guided-tour-settings", guidedTourSettingsList, List.class, HttpStatus.OK);
        List<GuidedTourSettings> updatedGuidedTourSettings = request.get("/api/guided-tour-settings", HttpStatus.OK, List.class);
    }
}
