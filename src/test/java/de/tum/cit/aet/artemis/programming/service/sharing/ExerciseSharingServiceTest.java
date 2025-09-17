package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import jakarta.ws.rs.NotFoundException;

import org.apache.commons.io.IOUtils;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.response.MockRestResponseCreators;

import de.tum.cit.aet.artemis.core.authentication.AuthenticationFactory;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Special tests for ExerciseSharingService focusing on validation failures.
 * Most of the functionality is tested via {@link ExerciseSharingResourceImportTest}
 * and {@link ExerciseSharingResourceExportTest}
 */
class ExerciseSharingServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String SAMPLE_BASKET_TOKEN = "sampleBasketToken";

    private static final String TEST_RETURN_URL = "http://unused.return.url/";

    private static final String TEST_PREFIX = "exerciseSharingServiceTest";

    public static final String INSTRUCTOR1 = TEST_PREFIX + "instructor";

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private ExerciseSharingService exerciseSharingService;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseImportFromSharingService programmingExerciseImportFromSharingService;

    @Autowired
    private VersionControlService versionControlService;

    @BeforeEach
    void startUp() throws Exception {
        sharingPlatformMockProvider.connectRequestFromSharingPlatform();
    }

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
        // reset caches!
        exerciseSharingService.getRepositoryCache().invalidateAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnFalseForInvalidTokenAndSecurityString() {
        assertThat(exerciseSharingService.validate("invalidToken", "invalid sec")).isFalse();
        // the token is invalid, however it would return the path
        // assertThat(exerciseSharingService.getExportedExerciseByToken("invalidToken")).isEmpty();
        assertThat(exerciseSharingService.getExportedExerciseByToken("invalidToken")).isEmpty();
    }

    @Test
    void shouldReturnFalseForNullTokenAndSecurityString() {
        assertThat(exerciseSharingService.validate(null, null)).isFalse();
        assertThat(exerciseSharingService.getExportedExerciseByToken(null)).isEmpty();
    }

    @Test
    void shouldReturnFalseForEmptyTokenAndSecurityString() {
        assertThat(exerciseSharingService.validate("", "")).isFalse();
        assertThat(exerciseSharingService.getExportedExerciseByToken("")).isEmpty();
    }

    @Test
    void shouldReturnFalseForExceedingTokenAndSecurityString() {
        String extraHugeToken = "Something" + "x".repeat(ExerciseSharingService.MAX_EXPORT_TOKEN_LENGTH);
        assertThat(exerciseSharingService.validate(extraHugeToken, "")).isFalse();
        assertThat(exerciseSharingService.getExportedExerciseByToken(extraHugeToken)).isEmpty();
    }

    @Test
    void shouldReturnEmptyBasketInfoOnNullToken() {
        assertThat(exerciseSharingService.getBasketInfo(null, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN)).isEmpty();
    }

    @Test
    void shouldReturnEmptyBasketInfoOnInvalidToken() {
        assertThat(exerciseSharingService.getBasketInfo("&%$!äöü", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN)).isEmpty();
    }

    @Test
    void testGetBasketInfo() throws URISyntaxException, IOException {
        mockSampleBasketLoadingForToken(SAMPLE_BASKET_TOKEN);

        Optional<ShoppingBasket> basket = exerciseSharingService.getBasketInfo(SAMPLE_BASKET_TOKEN, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN);

        assertThat(basket).isNotEmpty();
    }

    @Test
    void testGetBasketInfoWithFalseToken() throws URISyntaxException {
        URI basketJSONURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/FalseBasketToken");

        final ResponseActions responseActionsJSON = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketJSONURI))
                .andExpect(method(HttpMethod.GET));
        responseActionsJSON.andRespond(MockRestResponseCreators.withResourceNotFound());

        Optional<ShoppingBasket> basket = exerciseSharingService.getBasketInfo("FalseBasketToken", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN);

        assertThat(basket).isEmpty();
    }

    @Test
    void testGetBasketInfoWithServerFailure() throws URISyntaxException {
        URI basketJSONURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/FalseBasketToken");

        final ResponseActions responseActionsJSON = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketJSONURI))
                .andExpect(method(HttpMethod.GET));
        responseActionsJSON.andRespond(MockRestResponseCreators.withServerError());

        Optional<ShoppingBasket> basket = exerciseSharingService.getBasketInfo("FalseBasketToken", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN);

        assertThat(basket).isEmpty();
        // Could be more assertions, however not really helpful here, The details of the basket are tested in testGetExerciseInfoFromBasket
    }

    @Test
    void testGetBasketInfoWithEmptyToken() {
        Optional<ShoppingBasket> basket = exerciseSharingService.getBasketInfo("", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN);
        assertThat(basket).isEmpty();
    }

    @Test
    void testGetExerciseInfoFromBasket() throws URISyntaxException, IOException {
        getExerciseInfoFromBasket();
    }

    ProgrammingExercise getExerciseInfoFromBasket() throws URISyntaxException, IOException {
        mockSampleBasketLoadingForToken(SAMPLE_BASKET_TOKEN);
        mockSampleBasketZipForToken(SAMPLE_BASKET_TOKEN);

        SharingInfoDTO sharingInfo = new SharingInfoDTO(SAMPLE_BASKET_TOKEN, TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN,
                SharingPlatformMockProvider.calculateCorrectChecksum(sharingPlatformMockProvider.getTestSharingApiKey(), "returnURL", TEST_RETURN_URL, "apiBaseURL",
                        SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN),
                0);

        ProgrammingExercise exercise = exerciseSharingService.getExerciseDetailsFromBasket(sharingInfo);

        assertThat(exercise).isNotNull();

        return exercise;
    }

    @Test
    void testGetNonexistingExercise999InfoFromBasket() throws URISyntaxException, IOException {
        mockSampleBasketLoadingForToken(SAMPLE_BASKET_TOKEN);
        URI basketRepositoryZipURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + SAMPLE_BASKET_TOKEN + "/repository/999?format=artemis");

        final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketRepositoryZipURI))
                .andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withResourceNotFound());

        SharingInfoDTO sharingInfo = new SharingInfoDTO(SAMPLE_BASKET_TOKEN, TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN,
                SharingPlatformMockProvider.calculateCorrectChecksum(sharingPlatformMockProvider.getTestSharingApiKey(), "returnURL", TEST_RETURN_URL, "apiBaseURL",
                        SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN),
                999);

        assertThatThrownBy(() -> exerciseSharingService.getExerciseDetailsFromBasket(sharingInfo)).isInstanceOf(NotFoundException.class);

    }

    @Test
    void testGetNonexistingExerciseWithZipFailure() throws URISyntaxException, IOException {
        mockSampleBasketLoadingForToken(SAMPLE_BASKET_TOKEN);

        URI basketRepositoryZipURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + SAMPLE_BASKET_TOKEN + "/repository/0?format=artemis");

        final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketRepositoryZipURI))
                .andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withServerError());

        SharingInfoDTO sharingInfo = new SharingInfoDTO(SAMPLE_BASKET_TOKEN, TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN,
                SharingPlatformMockProvider.calculateCorrectChecksum(sharingPlatformMockProvider.getTestSharingApiKey(), "returnURL", TEST_RETURN_URL, "apiBaseURL",
                        SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN),
                0);

        assertThatThrownBy(() -> exerciseSharingService.getExerciseDetailsFromBasket(sharingInfo)).isInstanceOf(NotFoundException.class);

    }

    @Test
    // @Disabled("interferes with other tests :-(")
    @WithMockUser(username = INSTRUCTOR1, roles = "INSTRUCTOR")
    void testImportProgrammingExerciseFromSharing() throws URISyntaxException, IOException, GitAPIException, SharingException {
        userUtilService.addInstructor("Sharing", INSTRUCTOR1);

        SecurityContextHolder.getContext().setAuthentication(AuthenticationFactory.createUsernamePasswordAuthentication(INSTRUCTOR1.toLowerCase()));

        ProgrammingExercise exercise = getExerciseInfoFromBasket();
        Course course1 = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        SharingInfoDTO sharingInfo = new SharingInfoDTO(SAMPLE_BASKET_TOKEN, TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN,
                SharingPlatformMockProvider.calculateCorrectChecksum(sharingPlatformMockProvider.getTestSharingApiKey(), "returnURL", TEST_RETURN_URL, "apiBaseURL",
                        SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN),
                0);

        try {

            // doReturn(exercise).when(programmingExerciseImportFromFileService).importProgrammingExerciseFromFile(any(), any(), any(), any(), eq(true));
            SharingSetupInfo setupInfo = new SharingSetupInfo(exercise, course1, sharingInfo);

            ProgrammingExercise importedExercise = programmingExerciseImportFromSharingService.importProgrammingExerciseFromSharing(setupInfo);

            assertThat(importedExercise.getId()).isNotNull();
            assertThat(importedExercise.getPackageName()).isEqualTo(exercise.getPackageName());
            // Verify that the import pipeline was triggered once with the expected flag
            // verify(programmingExerciseImportFromFileService, times(1)).importProgrammingExerciseFromFile(any(), any(), any(), any(), eq(true));
        }
        finally {
            // we have to explicitely clean up the repository, otherwise repeated tests will fail, because the repository already exists.
            // we do this in a finally, because otherwise we would have to repeat it every afterEach.
            versionControlService.deleteProject(exercise.getProjectKey());
        }
    }

    @Test
    void trivialSharingExceptionTests() {
        SharingException e = new SharingException("trivial");
        assertThat(e.getMessage()).isEqualTo("trivial");

        SharingException e1 = new SharingException("trivial", e);
        assertThat(e1.getMessage()).isEqualTo("trivial");
        assertThat(e1.getCause()).isEqualTo(e);

        SharingException e2 = SharingException.withEndpoint("trivial", e);
        assertThat(e2.getMessage()).isEqualTo("Failed to connect to sharing platform at " + "trivial");
        assertThat(e2.getCause()).isEqualTo(e);

    }

    private void mockSampleBasketLoadingForToken(String basketToken) throws URISyntaxException, IOException {
        URI basketJSONURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + basketToken);
        try (InputStream in = Objects.requireNonNull(getClass().getResource("./basket/sampleBasket.json")).openStream()) {
            String basketJSON = IOUtils.toString(in, StandardCharsets.UTF_8);
            final ResponseActions responseActionsJSON = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketJSONURI))
                    .andExpect(method(HttpMethod.GET));
            responseActionsJSON.andRespond(MockRestResponseCreators.withSuccess(basketJSON, MediaType.APPLICATION_JSON));
        }
    }

    private void mockSampleBasketZipForToken(String basketToken) throws URISyntaxException, IOException {
        URI basketRepositoryZipURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + basketToken + "/repository/0?format=artemis");
        try (InputStream in = Objects.requireNonNull(getClass().getResource("./basket/sampleExercise.zip")).openStream()) {
            byte[] zippedBytes = IOUtils.toByteArray(in);
            final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketRepositoryZipURI))
                    .andExpect(method(HttpMethod.GET));
            responseActions.andRespond(MockRestResponseCreators.withSuccess(zippedBytes, MediaType.APPLICATION_OCTET_STREAM));
        }
    }

}
