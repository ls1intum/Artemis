package de.tum.cit.aet.artemis.communication.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.dto.LinkPreviewDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LinkPreviewIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "linkpreviewintegrationtest";

    // this link will return null for all fields because it does not include OG tags
    private static final String GOOGLE_URL = "https://google.com";

    private static final String MOCK_FILE_PATH_PREFIX = "src/test/java/de/tum/cit/aet/artemis/communication/linkpreview/mockFiles/";

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
    }

    @ParameterizedTest
    @MethodSource("provideUrls")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtraction(String url, File htmlFile) throws Exception {
        Document document = Jsoup.parse(htmlFile);

        try (MockedStatic<Jsoup> jsoupMock = Mockito.mockStatic(Jsoup.class)) {
            Connection connection = Mockito.mock(Connection.class);
            jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connection);

            // When Jsoup.connect(anyString()).get() is called, return the mocked Document
            when(connection.get()).thenReturn(document);

            // Construct the URL with an encoded query parameter
            String encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
            String requestUrl = "/api/link-preview?url=" + encodedUrl;

            // Perform a GET request with the URL as a query parameter
            LinkPreviewDTO linkPreviewData = request.get(requestUrl, HttpStatus.OK, LinkPreviewDTO.class);

            // Assert that the response is not null
            assertThat(linkPreviewData).isNotNull();

            if (url.equals(GOOGLE_URL)) {
                assertThat(linkPreviewData.url()).isNull();
                assertThat(linkPreviewData.description()).isNull();
                assertThat(linkPreviewData.image()).isNull();
                assertThat(linkPreviewData.title()).isNull();

                // check that the cache is available
                assertThat(Objects.requireNonNull(cacheManager.getCache("linkPreview")).get(url)).isNotNull();
            }
            else {
                assertThat(linkPreviewData.url()).isNotNull();
                assertThat(linkPreviewData.description()).isNotNull();
                assertThat(linkPreviewData.image()).isNotNull();
                assertThat(linkPreviewData.title()).isNotNull();
                assertThat(linkPreviewData.url()).isEqualTo(url);

                // check that the cache is available
                assertThat(Objects.requireNonNull(cacheManager.getCache("linkPreview")).get(url)).isNotNull();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtractionWithInvalidUrls() throws Exception {
        // Construct the URL with an encoded query parameter
        String requestUrl = "/api/link-preview?url=" + URLEncoder.encode("https://localhost", StandardCharsets.UTF_8);

        // Perform a GET request with the URL as a query parameter and make sure it throws
        request.get(requestUrl, HttpStatus.BAD_REQUEST, LinkPreviewDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtractionWithIpAddress() throws Exception {
        // Test a blocked private IPv4 address
        String requestUrl = "/api/link-preview?url=" + URLEncoder.encode("http://192.168.1.1", StandardCharsets.UTF_8);
        request.get(requestUrl, HttpStatus.BAD_REQUEST, LinkPreviewDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtractionWithLoopbackIp() throws Exception {
        // Test loopback address
        String requestUrl = "/api/link-preview?url=" + URLEncoder.encode("http://127.0.0.1", StandardCharsets.UTF_8);
        request.get(requestUrl, HttpStatus.BAD_REQUEST, LinkPreviewDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtractionWithIpv6Address() throws Exception {
        // Test a blocked IPv6 address
        String requestUrl = "/api/link-preview?url=" + URLEncoder.encode("http://[::1]", StandardCharsets.UTF_8);
        request.get(requestUrl, HttpStatus.BAD_REQUEST, LinkPreviewDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtractionWithFullIpv6Address() throws Exception {
        // Test a blocked IPv6 address
        String requestUrl = "/api/link-preview?url=" + URLEncoder.encode("http://[2606:4700:4700::1111]", StandardCharsets.UTF_8);
        request.get(requestUrl, HttpStatus.BAD_REQUEST, LinkPreviewDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtractionWithInvalidScheme() throws Exception {
        // Test a non-http/https scheme
        String requestUrl = "/api/link-preview?url=" + URLEncoder.encode("ftp://example.com", StandardCharsets.UTF_8);
        request.get(requestUrl, HttpStatus.BAD_REQUEST, LinkPreviewDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtractionWithJavaScriptUrl() throws Exception {
        // Test a JavaScript-based URL
        String requestUrl = "/api/link-preview?url=" + URLEncoder.encode("javascript:void(0);", StandardCharsets.UTF_8);
        request.get(requestUrl, HttpStatus.BAD_REQUEST, LinkPreviewDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtractionWithInvalidTld() throws Exception {
        // Test an invalid top-level domain (TLD)
        String requestUrl = "/api/link-preview?url=" + URLEncoder.encode("https://example.superlongtldover20chars", StandardCharsets.UTF_8);
        request.get(requestUrl, HttpStatus.BAD_REQUEST, LinkPreviewDTO.class);
    }

    private static Stream<Arguments> provideUrls() {
        var mockPath = Path.of(MOCK_FILE_PATH_PREFIX);
        return Stream.of(Arguments.of("https://github.com/ls1intum/Artemis/pull/6615", mockPath.resolve("github_pull_request_6615.txt").toFile()),
                Arguments.of("https://github.com/ls1intum/Artemis/pull/6618", mockPath.resolve("github_pull_request_6618.txt").toFile()),
                Arguments.of("https://github.com/", mockPath.resolve("github_home.txt").toFile()), Arguments.of(GOOGLE_URL, mockPath.resolve("google.txt").toFile()));
    }
}
