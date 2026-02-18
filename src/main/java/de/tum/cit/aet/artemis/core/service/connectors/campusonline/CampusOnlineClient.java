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
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineCourseMetadataResponse;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCoursesResponse;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineStudentListResponse;

@Service
@Conditional(CampusOnlineEnabled.class)
@Profile(PROFILE_CORE)
@Lazy
public class CampusOnlineClient {

    private static final Logger log = LoggerFactory.getLogger(CampusOnlineClient.class);

    private static final XmlMapper xmlMapper;

    static {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        xmlMapper = new XmlMapper(new XmlFactory(inputFactory));
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private final RestTemplate restTemplate;

    @Value("${artemis.campus-online.base-url}")
    private String baseUrl;

    @Value("${artemis.campus-online.tokens}")
    private List<String> tokens;

    public CampusOnlineClient(@Qualifier("campusOnlineRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches the list of students enrolled in a CAMPUSOnline course.
     *
     * @param courseId the CAMPUSOnline course ID
     * @return the parsed student list response
     */
    public CampusOnlineStudentListResponse fetchStudents(String courseId) {
        String url = buildUrl("/cdm/course/students/xml", "courseID", courseId);
        String xml = fetchWithTokenFallback(url);
        return parseXml(xml, CampusOnlineStudentListResponse.class);
    }

    /**
     * Fetches metadata for a single CAMPUSOnline course.
     *
     * @param courseId the CAMPUSOnline course ID
     * @return the parsed course metadata response
     */
    public CampusOnlineCourseMetadataResponse fetchCourseMetadata(String courseId) {
        String url = buildUrl("/cdm/course/xml", "courseID", courseId);
        String xml = fetchWithTokenFallback(url);
        return parseXml(xml, CampusOnlineCourseMetadataResponse.class);
    }

    /**
     * Fetches courses for an organizational unit within a date range.
     *
     * @param orgUnitId the organizational unit ID
     * @param from      the start date (format: YYYY-MM-DD)
     * @param until     the end date (format: YYYY-MM-DD)
     * @return the parsed org courses response
     */
    public CampusOnlineOrgCoursesResponse fetchCoursesForOrg(String orgUnitId, String from, String until) {
        String url = buildUrl("/xcal/organization/courses/xml", "orgUnitID", orgUnitId, "from", from, "until", until);
        String xml = fetchWithTokenFallback(url);
        return parseXml(xml, CampusOnlineOrgCoursesResponse.class);
    }

    private String buildUrl(String path, String... queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + path);
        for (int i = 0; i < queryParams.length - 1; i += 2) {
            builder.queryParam(queryParams[i], queryParams[i + 1]);
        }
        return builder.build().toUriString();
    }

    private String fetchWithTokenFallback(String urlWithoutToken) {
        if (tokens == null || tokens.isEmpty()) {
            throw new CampusOnlineApiException("No CAMPUSOnline API tokens configured");
        }

        RestClientException lastException = null;
        for (String token : tokens) {
            try {
                String url = UriComponentsBuilder.fromUriString(urlWithoutToken).queryParam("token", token).build().toUriString();
                String response = restTemplate.getForObject(url, String.class);
                if (response != null) {
                    return response;
                }
            }
            catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                    log.warn("CAMPUSOnline API call failed with authentication error ({}), trying next token", e.getStatusCode());
                    lastException = e;
                }
                else {
                    throw new CampusOnlineApiException("CAMPUSOnline API returned error: " + e.getStatusCode(), e);
                }
            }
            catch (RestClientException e) {
                log.warn("CAMPUSOnline API call failed, trying next token: {}", e.getClass().getSimpleName());
                lastException = e;
            }
        }
        throw new CampusOnlineApiException("All CAMPUSOnline API tokens failed", lastException);
    }

    private <T> T parseXml(String xml, Class<T> clazz) {
        try {
            return xmlMapper.readValue(xml, clazz);
        }
        catch (Exception e) {
            throw new CampusOnlineApiException("Failed to parse CAMPUSOnline XML response for " + clazz.getSimpleName(), e);
        }
    }
}
