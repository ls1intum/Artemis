package de.tum.in.www1.artemis.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import de.tum.in.www1.artemis.domain.GuidedTourSettings;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class GuidedTourSettingsResourceTest {

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Before
    public void initTestCase() {
        database.resetDatabase();
        database.addUsers(1, 0, 0);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getDefaultGuidedTourSettings() throws Exception {
        GuidedTourSettings guidedTourSettings = request.get("/api/guided-tour-settings", HttpStatus.OK, GuidedTourSettings.class);
        assertThat(guidedTourSettings.isShowCourseOverviewTour()).as("show course overview tour").isTrue();
        assertThat(guidedTourSettings.isShowModelingExerciseTour()).as("show modeling exercise tour").isTrue();
        assertThat(guidedTourSettings.isShowProgrammingExerciseTour()).as("show programming exercise tour").isTrue();
        assertThat(guidedTourSettings.isShowQuizExerciseTour()).as("show quiz exercise tour").isTrue();
        assertThat(guidedTourSettings.isShowModelingExerciseTour()).as("show modeling exercise tour").isTrue();
        assertThat(guidedTourSettings.isShowTextExerciseTour()).as("show text exercise tour").isTrue();
    }

    @Test
    @WithMockUser(value = "student1")
    public void updateGuidedTourSettings() throws Exception {
        GuidedTourSettings guidedTourSettings = new GuidedTourSettings(true, false, true, false, true, true);
        request.putWithResponseBody("/api/guided-tour-settings", guidedTourSettings, GuidedTourSettings.class, HttpStatus.OK);

        GuidedTourSettings updatedGuidedTourSettings = request.get("/api/guided-tour-settings", HttpStatus.OK, GuidedTourSettings.class);
        assertThat(updatedGuidedTourSettings.isShowCourseOverviewTour()).as("show course overview tour").isTrue();
        assertThat(updatedGuidedTourSettings.isShowNavigationTour()).as("don't show navigation tour").isFalse();
        assertThat(updatedGuidedTourSettings.isShowProgrammingExerciseTour()).as("show programming exercise tour").isTrue();
        assertThat(updatedGuidedTourSettings.isShowQuizExerciseTour()).as("don't show quiz exercise tour").isFalse();
        assertThat(updatedGuidedTourSettings.isShowModelingExerciseTour()).as("show modeling exercise tour").isTrue();
        assertThat(updatedGuidedTourSettings.isShowTextExerciseTour()).as("show text exercise tour").isTrue();
    }
}
