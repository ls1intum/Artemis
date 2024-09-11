package de.tum.cit.aet.artemis.settings.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.settings.ide.Ide;
import de.tum.cit.aet.artemis.domain.settings.ide.UserIdeMapping;
import de.tum.cit.aet.artemis.repository.settings.IdeRepository;
import de.tum.cit.aet.artemis.repository.settings.UserIdeMappingRepository;
import de.tum.cit.aet.artemis.web.rest.dto.settings.ide.IdeDTO;
import de.tum.cit.aet.artemis.web.rest.dto.settings.ide.IdeMappingDTO;

class IdePreferencesIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "idepreferencesintegration";

    @Autowired
    private UserIdeMappingRepository userIdeMappingRepository;

    @Autowired
    private IdeRepository ideRepository;

    private Ide VsCode;

    private Ide IntelliJ;

    private User student1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        VsCode = new Ide("VS Code", "vscode://vscode.git/clone?url={cloneUrl}");
        IntelliJ = new Ide("IntelliJ", "jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}");
    }

    @AfterEach
    void tearDown() {
        userIdeMappingRepository.deleteAll();
        ideRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetIdePreferencesForCurrentUser() throws Exception {
        ideRepository.save(IntelliJ);
        ideRepository.save(VsCode);
        userIdeMappingRepository.save(new UserIdeMapping(student1, ProgrammingLanguage.EMPTY, IntelliJ));
        userIdeMappingRepository.save(new UserIdeMapping(student1, ProgrammingLanguage.SWIFT, VsCode));
        userIdeMappingRepository.save(new UserIdeMapping(student1, ProgrammingLanguage.JAVA, IntelliJ));

        List<IdeMappingDTO> idePreferences = request.getList("/api/ide-settings", HttpStatus.OK, IdeMappingDTO.class);

        assertThat(idePreferences).as("deeplink for no programming Language is intellij")
                .anyMatch(ideMappingDTO -> ideMappingDTO.programmingLanguage().equals(ProgrammingLanguage.EMPTY) && ideMappingDTO.ide().deepLink().equals(IntelliJ.getDeepLink()));

        assertThat(idePreferences).as("deeplink for Swift is vscode")
                .anyMatch(ideMappingDTO -> ideMappingDTO.programmingLanguage().equals(ProgrammingLanguage.SWIFT) && ideMappingDTO.ide().deepLink().equals(VsCode.getDeepLink()));

        assertThat(idePreferences).as("deeplink for Java is intellij")
                .anyMatch(ideMappingDTO -> ideMappingDTO.programmingLanguage().equals(ProgrammingLanguage.JAVA) && ideMappingDTO.ide().deepLink().equals(IntelliJ.getDeepLink()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSetNewIdePreferenceForCurrentUser() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("programmingLanguage", ProgrammingLanguage.SWIFT.toString());

        IdeMappingDTO idePreference = request.putWithResponseBodyAndParams("/api/ide-settings", new IdeDTO(IntelliJ), IdeMappingDTO.class, HttpStatus.OK, params);

        assertThat(idePreference.programmingLanguage()).as("new ide preference for Swift").isEqualTo(ProgrammingLanguage.SWIFT);
        assertThat(idePreference.ide().deepLink()).as("new preference for Swift is intelliJ").isEqualTo(IntelliJ.getDeepLink());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testChangeIdePreferenceForCurrentUser() throws Exception {
        ideRepository.save(VsCode);
        userIdeMappingRepository.save(new UserIdeMapping(student1, ProgrammingLanguage.SWIFT, VsCode));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("programmingLanguage", ProgrammingLanguage.SWIFT.toString());

        IdeMappingDTO idePreference = request.putWithResponseBodyAndParams("/api/ide-settings", new IdeDTO(IntelliJ), IdeMappingDTO.class, HttpStatus.OK, params);
        assertThat(idePreference.programmingLanguage()).as("deeplink for Swift changed").isEqualTo(ProgrammingLanguage.SWIFT);
        assertThat(idePreference.ide().deepLink()).as("deeplink for Swift is now intelliJ").isEqualTo(IntelliJ.getDeepLink());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteIdePreferenceForCurrentUser() throws Exception {
        ideRepository.save(VsCode);
        userIdeMappingRepository.save(new UserIdeMapping(student1, ProgrammingLanguage.SWIFT, VsCode));

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("programmingLanguage", ProgrammingLanguage.SWIFT.toString());
        request.delete("/api/ide-settings", HttpStatus.OK, params);
        assertThat(userIdeMappingRepository.findAllByUserId(student1.getId())).as("ide preference got deleted").isEmpty();
    }

}
