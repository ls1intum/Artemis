package de.tum.cit.aet.artemis.core.service.connectors.campusonline;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import javax.xml.stream.XMLInputFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import de.tum.cit.aet.artemis.core.config.CampusOnlineEnabled;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineCourseMetadataResponseDTO;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCoursesResponseDTO;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineStudentListResponseDTO;

/**
 * HTTP client service for the CAMPUSOnline external API.
 * <p>
 * This service handles all outgoing REST calls to the CAMPUSOnline CDM/xCal endpoints,
 * including XML response deserialization and automatic token-fallback authentication.
 * Multiple API tokens can be configured; if the first token fails with an authentication
 * error, the service transparently retries with the next token.
 * <p>
 * The XML parser is configured to disable DTD and external entity processing
 * to prevent XXE attacks.
 */
@Service
@Conditional(CampusOnlineEnabled.class)
@Profile(PROFILE_CORE)
@Lazy
public class CampusOnlineClientService {

    private static final Logger log = LoggerFactory.getLogger(CampusOnlineClientService.class);

    /**
     * Thread-safe, reusable XML mapper configured with XXE protections disabled.
     * Initialized once in the static block and shared across all instances.
     */
    private static final XmlMapper xmlMapper;

    static {
        // Configure XMLInputFactory with XXE protections to prevent XML External Entity attacks
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        xmlMapper = new XmlMapper(new XmlFactory(inputFactory));
        // Ignore unknown XML elements to allow forward-compatible deserialization
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private final RestTemplate restTemplate;

    /** Base URL of the CAMPUSOnline instance (e.g. "https://campus.tum.de/tumonline"). */
    private final String baseUrl;

    /** Ordered list of API tokens; tried sequentially until one succeeds. */
    private final List<String> tokens;

    /**
     * Constructs the client service with the required dependencies.
     *
     * @param restTemplate the dedicated RestTemplate for CAMPUSOnline API calls
     * @param baseUrl      the base URL of the CAMPUSOnline instance
     * @param tokens       the list of API tokens for authentication (tried in order)
     */
    public CampusOnlineClientService(@Qualifier("campusOnlineRestTemplate") RestTemplate restTemplate, @Value("${artemis.campus-online.base-url}") String baseUrl,
            @Value("${artemis.campus-online.tokens}") List<String> tokens) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.tokens = tokens;
    }

    /**
     * Fetches the list of students enrolled in a CAMPUSOnline course.
     *
     * @param courseId the CAMPUSOnline course ID
     * @return the parsed student list response
     */
    public CampusOnlineStudentListResponseDTO fetchStudents(String courseId) {
        String url = buildUrl("/cdm/course/students/xml", "courseID", courseId);
        String xml = fetchWithTokenFallback(url);
        return parseXml(xml, CampusOnlineStudentListResponseDTO.class);
    }

    /**
     * Fetches metadata for a single CAMPUSOnline course.
     *
     * @param courseId the CAMPUSOnline course ID
     * @return the parsed course metadata response
     */
    public CampusOnlineCourseMetadataResponseDTO fetchCourseMetadata(String courseId) {
        String url = buildUrl("/cdm/course/xml", "courseID", courseId);
        String xml = fetchWithTokenFallback(url);
        return parseXml(xml, CampusOnlineCourseMetadataResponseDTO.class);
    }

    /**
     * Fetches courses for an organizational unit within a date range.
     *
     * @param orgUnitId the organizational unit ID
     * @param from      the start date (format: YYYY-MM-DD)
     * @param until     the end date (format: YYYY-MM-DD)
     * @return the parsed org courses response
     */
    public CampusOnlineOrgCoursesResponseDTO fetchCoursesForOrg(String orgUnitId, String from, String until) {
        String url = buildUrl("/xcal/organization/courses/xml", "orgUnitID", orgUnitId, "from", from, "until", until);
        String xml = fetchWithTokenFallback(url);
        return parseXml(xml, CampusOnlineOrgCoursesResponseDTO.class);
    }

    /**
     * Builds a complete URL by appending the given path to the base URL and adding query parameters.
     *
     * @param path        the API endpoint path (e.g. "/cdm/course/students/xml")
     * @param queryParams alternating key-value pairs for query parameters
     * @return the fully constructed URL string
     */
    private String buildUrl(String path, String... queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        // Query params are provided as alternating key-value pairs
        for (int i = 0; i < queryParams.length - 1; i += 2) {
            builder.queryParam(queryParams[i], queryParams[i + 1]);
        }
        return builder.build().toUriString();
    }

    /**
     * Executes an HTTP GET request with automatic token-fallback authentication.
     * <p>
     * Tries each configured API token in order. If a token results in a 401/403 error,
     * the next token is attempted. Non-authentication errors are propagated immediately.
     * If a token returns a {@code null} response (e.g. HTTP 204), it is treated as
     * an authentication/authorization issue and the next token is tried.
     *
     * @param urlWithoutToken the complete URL without the token query parameter
     * @return the raw XML response body
     * @throws CampusOnlineApiException if all tokens fail or an unexpected error occurs
     */
    private String fetchWithTokenFallback(String urlWithoutToken) {
        if (tokens == null || tokens.isEmpty()) {
            throw new CampusOnlineApiException("No CAMPUSOnline API tokens configured");
        }

        RestClientException lastException = null;
        boolean receivedNullResponse = false;
        for (String token : tokens) {
            try {
                // Append the token as a query parameter for authentication
                String url = UriComponentsBuilder.fromUriString(urlWithoutToken).queryParam("token", token).build().toUriString();
                String response = restTemplate.getForObject(url, String.class);
                if (response != null) {
                    return response;
                }
                // Null response (e.g. HTTP 204) — treat as token issue and try next
                log.warn("CAMPUSOnline API returned null response, trying next token");
                receivedNullResponse = true;
            }
            catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                    // Authentication/authorization failure — try next token
                    log.warn("CAMPUSOnline API call failed with authentication error ({}), trying next token", e.getStatusCode());
                    lastException = e;
                }
                else {
                    // Non-auth HTTP error — propagate immediately (do not include cause to avoid token leakage in logs)
                    throw new CampusOnlineApiException("CAMPUSOnline API returned error: " + e.getStatusCode());
                }
            }
            catch (RestClientException e) {
                // Network or other REST client error — try next token
                log.warn("CAMPUSOnline API call failed, trying next token: {}", e.getClass().getSimpleName());
                lastException = e;
            }
        }
        // Do not include the original exception as cause to avoid leaking API tokens (which are embedded in the request URL)
        if (receivedNullResponse && lastException == null) {
            throw new CampusOnlineApiException("CAMPUSOnline API returned null/empty response for all configured tokens");
        }
        throw new CampusOnlineApiException("All CAMPUSOnline API tokens failed");
    }

    /**
     * Deserializes an XML string into the specified DTO type using the configured {@link XmlMapper}.
     *
     * @param xml   the raw XML response string
     * @param clazz the target class to deserialize into
     * @param <T>   the DTO type
     * @return the deserialized DTO instance
     * @throws CampusOnlineApiException if XML parsing fails
     */
    private <T> T parseXml(String xml, Class<T> clazz) {
        try {
            return xmlMapper.readValue(xml, clazz);
        }
        catch (Exception e) {
            throw new CampusOnlineApiException("Failed to parse CAMPUSOnline XML response for " + clazz.getSimpleName(), e);
        }
    }
}
