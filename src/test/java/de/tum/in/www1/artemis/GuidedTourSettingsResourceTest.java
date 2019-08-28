package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.*;
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
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class GuidedTourSettingsResourceTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 0, 0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1")
    public void guidedTourSettingsIsInitiallyNull() throws Exception {
        User user = request.get("/api/account", HttpStatus.OK, User.class);
        assertThat(user.getGuidedTourSettings().isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(value = "student1")
    @SuppressWarnings("unchecked")
    public void updateGuidedTourSettings() throws Exception {
        Set<GuidedTourSettings> guidedTourSettingsSet = new HashSet<>();

        GuidedTourSettings guidedTourSettings1 = new GuidedTourSettings();
        guidedTourSettings1.setGuidedTourKey("course_overview_tour");
        guidedTourSettings1.setGuidedTourStep(5);
        guidedTourSettings1.setGuidedTourState(GuidedTourSettings.Status.FINISHED);

        GuidedTourSettings guidedTourSettings2 = new GuidedTourSettings();
        guidedTourSettings2.setGuidedTourKey("new_tour");
        guidedTourSettings2.setGuidedTourStep(7);
        guidedTourSettings2.setGuidedTourState(GuidedTourSettings.Status.STARTED);

        guidedTourSettingsSet.add(guidedTourSettings1);
        guidedTourSettingsSet.add(guidedTourSettings2);
        Set<GuidedTourSettings> serverGuidedTourSettings = request.putWithResponseBody("/api/guided-tour-settings", guidedTourSettingsSet, Set.class, HttpStatus.OK);
        assertThat(serverGuidedTourSettings).isNotNull();
        assertThat(serverGuidedTourSettings.isEmpty()).isFalse();
        assertThat(serverGuidedTourSettings.size()).isEqualTo(2);

        User user = request.get("/api/account", HttpStatus.OK, User.class);
        assertThat(user.getGuidedTourSettings()).isNotNull();
        assertThat(user.getGuidedTourSettings().isEmpty()).isFalse();
        assertThat(user.getGuidedTourSettings().size()).isEqualTo(2);
    }
}
