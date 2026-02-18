package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineApiException;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineClient;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineCourseMetadataResponse;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCoursesResponse;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineStudentListResponse;

/**
 * Unit tests for {@link CampusOnlineClient}.
 * Tests token fallback logic, XML parsing error handling, and API error scenarios.
 */
class CampusOnlineClientTest {

    private RestTemplate restTemplate;

    private CampusOnlineClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new CampusOnlineClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "https://campus.example.com");
        ReflectionTestUtils.setField(client, "tokens", List.of("token1", "token2"));
    }

    // ==================== Token fallback scenarios ====================

    @Test
    void fetchStudents_shouldSucceedWithFirstToken() {
        String xml = "<students></students>";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(xml);

        CampusOnlineStudentListResponse result = client.fetchStudents("CO-101");

        assertThat(result).isNotNull();
        // Should only call once since first token works
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void fetchStudents_shouldFallbackToSecondToken_on401() {
        String xml = "<students></students>";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED)).thenReturn(xml);

        CampusOnlineStudentListResponse result = client.fetchStudents("CO-101");

        assertThat(result).isNotNull();
        verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void fetchStudents_shouldFallbackToSecondToken_on403() {
        String xml = "<students></students>";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN)).thenReturn(xml);

        CampusOnlineStudentListResponse result = client.fetchStudents("CO-101");

        assertThat(result).isNotNull();
        verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void fetchStudents_shouldThrowImmediately_onNonAuthError() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchStudents("CO-101")).isInstanceOf(CampusOnlineApiException.class).hasMessageContaining("CAMPUSOnline API returned error: 404");

        // Should NOT try the second token
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void fetchStudents_shouldThrow_whenAllTokensFail() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.fetchStudents("CO-101")).isInstanceOf(CampusOnlineApiException.class).hasMessageContaining("All CAMPUSOnline API tokens failed");

        verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void fetchStudents_shouldFallbackOnRestClientException() {
        String xml = "<students></students>";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new ResourceAccessException("Connection timeout")).thenReturn(xml);

        CampusOnlineStudentListResponse result = client.fetchStudents("CO-101");

        assertThat(result).isNotNull();
        verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void fetchStudents_shouldThrow_whenNoTokensConfigured() {
        ReflectionTestUtils.setField(client, "tokens", List.of());

        assertThatThrownBy(() -> client.fetchStudents("CO-101")).isInstanceOf(CampusOnlineApiException.class).hasMessageContaining("No CAMPUSOnline API tokens configured");
    }

    @Test
    void fetchStudents_shouldThrow_whenTokensNull() {
        ReflectionTestUtils.setField(client, "tokens", null);

        assertThatThrownBy(() -> client.fetchStudents("CO-101")).isInstanceOf(CampusOnlineApiException.class).hasMessageContaining("No CAMPUSOnline API tokens configured");
    }

    // ==================== XML parsing error handling ====================

    @Test
    void fetchStudents_shouldThrow_whenXmlIsInvalid() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("not valid xml <<<<");

        assertThatThrownBy(() -> client.fetchStudents("CO-101")).isInstanceOf(CampusOnlineApiException.class)
                .hasMessageContaining("Failed to parse CAMPUSOnline XML response for CampusOnlineStudentListResponse");
    }

    @Test
    void fetchCourseMetadata_shouldThrow_whenXmlIsInvalid() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{this is json, not xml}");

        assertThatThrownBy(() -> client.fetchCourseMetadata("CO-101")).isInstanceOf(CampusOnlineApiException.class)
                .hasMessageContaining("Failed to parse CAMPUSOnline XML response for CampusOnlineCourseMetadataResponse");
    }

    // ==================== Successful parsing ====================

    @Test
    void fetchCourseMetadata_shouldParseValidXml() {
        String xml = """
                <cdm>
                    <courseName>Test Course</courseName>
                    <teachingTerm>2025W</teachingTerm>
                    <courseLanguage>EN</courseLanguage>
                    <courseID>CO-101</courseID>
                </cdm>
                """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(xml);

        CampusOnlineCourseMetadataResponse result = client.fetchCourseMetadata("CO-101");

        assertThat(result.courseName()).isEqualTo("Test Course");
        assertThat(result.teachingTerm()).isEqualTo("2025W");
        assertThat(result.courseLanguage()).isEqualTo("EN");
    }

    @Test
    void fetchCoursesForOrg_shouldParseValidXml() {
        String xml = """
                <courses>
                    <course>
                        <courseID>CO-101</courseID>
                        <courseName>Test Course</courseName>
                        <teachingTerm>2025W</teachingTerm>
                    </course>
                </courses>
                """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(xml);

        CampusOnlineOrgCoursesResponse result = client.fetchCoursesForOrg("999", "2025-01-01", "2025-12-31");

        assertThat(result.courses()).hasSize(1);
        assertThat(result.courses().getFirst().courseId()).isEqualTo("CO-101");
        assertThat(result.courses().getFirst().courseName()).isEqualTo("Test Course");
    }
}
