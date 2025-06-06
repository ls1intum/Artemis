package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ide.Ide;
import de.tum.cit.aet.artemis.programming.domain.ide.UserIdeMapping;
import de.tum.cit.aet.artemis.programming.dto.IdeDTO;
import de.tum.cit.aet.artemis.programming.dto.IdeMappingDTO;

class IdePreferencesIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "idepreferencesintegration";

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

        List<IdeMappingDTO> idePreferences = request.getList("/api/programming/ide-settings", HttpStatus.OK, IdeMappingDTO.class);

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

        IdeMappingDTO idePreference = request.putWithResponseBodyAndParams("/api/programming/ide-settings", new IdeDTO(IntelliJ), IdeMappingDTO.class, HttpStatus.OK, params);

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

        IdeMappingDTO idePreference = request.putWithResponseBodyAndParams("/api/programming/ide-settings", new IdeDTO(IntelliJ), IdeMappingDTO.class, HttpStatus.OK, params);
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
        request.delete("/api/programming/ide-settings", HttpStatus.OK, params);
        assertThat(userIdeMappingRepository.findAllByUserId(student1.getId())).as("ide preference got deleted").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSetIdePreferenceForUnsupportedLanguage() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("programmingLanguage", "UNSUPPORTED_LANGUAGE");
        request.putWithResponseBodyAndParams("/api/programming/ide-settings", new IdeDTO(IntelliJ), IdeMappingDTO.class, HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSetPreferenceWithNonExistentIde() throws Exception {
        Ide nonExistentIde = new Ide("NonExistent", "nonexistent://url");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("programmingLanguage", ProgrammingLanguage.JAVA.toString());
        request.putWithResponseBodyAndParams("/api/programming/ide-settings", new IdeDTO(nonExistentIde), IdeMappingDTO.class, HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPredefinedIdes() throws Exception {
        List<IdeDTO> predefinedIdes = request.getList("/api/programming/ide-settings/predefined", HttpStatus.OK, IdeDTO.class);
        assertThat(predefinedIdes).isNotEmpty();
        assertThat(predefinedIdes).extracting("name").contains("VS Code", "IntelliJ");
        assertThat(predefinedIdes).allMatch(ideDTO -> ideDTO.deepLink() != null && !ideDTO.deepLink().isEmpty() && ideDTO.deepLink().contains("{cloneUrl}"));
    }

}
