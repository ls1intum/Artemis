package de.tum.in.www1.artemis.metis.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.service.linkpreview.ogparser.Content;
import de.tum.in.www1.artemis.service.linkpreview.ogparser.OpenGraph;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.LinkPreviewDTO;

@ExtendWith(MockitoExtension.class)
class LinkPreviewIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "linkpreviewintegrationtest";

    // this link will return null for all fields because it does not include OG tags
    private static final String GOOGLE_URL = "https://google.com";

    private static final String[] URLS = { "https://github.com/ls1intum/Artemis/pull/6615", "https://github.com/ls1intum/Artemis/pull/6618", "https://github.com/", GOOGLE_URL };

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CacheManager cacheManager;

    @Mock
    private OpenGraph mockOpenGraph;

    @Mock
    private Content mockContent;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
    }

    @ParameterizedTest
    @MethodSource("provideUrls")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLinkPreviewDataExtraction(String url) throws Exception {
        // Mock the part where the URL has to be parsed
        mockOpenGraph(url);

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

    /**
     * Mocks the OpenGraph object and its content for the given url.
     *
     * @param url the url
     */
    private void mockOpenGraph(String url) {
        Mockito.when(ogParser.getOpenGraphOf(url)).thenReturn(mockOpenGraph);
        Mockito.when(mockOpenGraph.getContentOf("title")).thenReturn(mockContent);
        Mockito.when(mockOpenGraph.getContentOf("description")).thenReturn(mockContent);
        Mockito.when(mockOpenGraph.getContentOf("image")).thenReturn(mockContent);
        Mockito.when(mockOpenGraph.getContentOf("url")).thenReturn(mockContent);
        if (!GOOGLE_URL.equals(url)) {
            Mockito.when(mockContent.getValue()).thenReturn(url);
        }
    }

    private static Stream<String> provideUrls() {
        return Stream.of(URLS);
    }
}
