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

import de.tum.in.www1.artemis.domain.GuidedTourSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class GuidedTourSettingResourceTest {

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
        Set<GuidedTourSetting> serverGuidedTourSettings = request.putWithResponseBody("/api/guided-tour-settings", guidedTourSettingSet, Set.class, HttpStatus.OK);
        assertThat(serverGuidedTourSettings).isNotNull();
        assertThat(serverGuidedTourSettings.isEmpty()).isFalse();
        assertThat(serverGuidedTourSettings.size()).isEqualTo(2);

        User user = request.get("/api/account", HttpStatus.OK, User.class);
        assertThat(user.getGuidedTourSettings()).isNotNull();
        assertThat(user.getGuidedTourSettings().isEmpty()).isFalse();
        assertThat(user.getGuidedTourSettings().size()).isEqualTo(2);
    }
}
