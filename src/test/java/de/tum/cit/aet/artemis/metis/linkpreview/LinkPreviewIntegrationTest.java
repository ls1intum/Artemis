package de.tum.cit.aet.artemis.metis.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Objects;
import java.util.stream.Stream;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.web.rest.dto.LinkPreviewDTO;

class LinkPreviewIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "linkpreviewintegrationtest";

    // this link will return null for all fields because it does not include OG tags
    private static final String GOOGLE_URL = "https://google.com";

    private static final String MOCK_FILE_PATH_PREFIX = "src/test/java/de/tum/cit/aet/artemis/metis/linkpreview/mockFiles/";

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

            LinkPreviewDTO linkPreviewData = request.postWithPlainStringResponseBody("/api/link-preview", url, LinkPreviewDTO.class, HttpStatus.OK);
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

    private static Stream<Arguments> provideUrls() {
        return Stream.of(Arguments.of("https://github.com/ls1intum/Artemis/pull/6615", new File(MOCK_FILE_PATH_PREFIX + "github_pull_request_6615.txt")),
                Arguments.of("https://github.com/ls1intum/Artemis/pull/6618", new File(MOCK_FILE_PATH_PREFIX + "github_pull_request_6618.txt")),
                Arguments.of("https://github.com/", new File(MOCK_FILE_PATH_PREFIX + "github_home.txt")), Arguments.of(GOOGLE_URL, new File(MOCK_FILE_PATH_PREFIX + "google.txt")));
    }
}
