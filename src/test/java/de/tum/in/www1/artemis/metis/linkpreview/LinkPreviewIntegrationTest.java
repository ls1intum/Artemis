package de.tum.in.www1.artemis.metis.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.LinkPreviewDTO;

class LinkPreviewIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "linkpreviewintegrationtest";

    private static final String[] urls = { "https://github.com/ls1intum/Artemis/pull/6615",
            "https://stackoverflow.com/questions/40965622/unit-testing-an-endpoint-with-requestbody", "https://github.com/" };

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
    }

    @ParameterizedTest
    @MethodSource("provideUrls")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateConversationPost(String url) throws Exception {

        LinkPreviewDTO linkPreviewData = request.postWithPlainStringResponseBody("/api/link-preview", url, LinkPreviewDTO.class, HttpStatus.OK);
        assertThat(linkPreviewData).isNotNull();

        assertThat(linkPreviewData.url()).isNotNull();
        assertThat(linkPreviewData.description()).isNotNull();
        assertThat(linkPreviewData.image()).isNotNull();
        assertThat(linkPreviewData.title()).isNotNull();

        assertThat(linkPreviewData.url()).isEqualTo(url);
    }

    private static Stream<String> provideUrls() {
        return Stream.of(urls);
    }
}
