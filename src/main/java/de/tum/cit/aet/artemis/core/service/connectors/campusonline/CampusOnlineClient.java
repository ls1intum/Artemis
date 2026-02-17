package de.tum.cit.aet.artemis.core.service.connectors.campusonline;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import de.tum.cit.aet.artemis.core.config.CampusOnlineEnabled;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineCourseMetadataResponse;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCoursesResponse;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineStudentListResponse;

@Service
@Conditional(CampusOnlineEnabled.class)
@Lazy
public class CampusOnlineClient {

    private static final Logger log = LoggerFactory.getLogger(CampusOnlineClient.class);

    private static final XmlMapper xmlMapper = new XmlMapper();

    private final RestTemplate restTemplate;

    @Value("${artemis.campus-online.base-url}")
    private String baseUrl;

    @Value("${artemis.campus-online.tokens}")
    private List<String> tokens;

    public CampusOnlineClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches the list of students enrolled in a CAMPUSOnline course.
     *
     * @param courseId the CAMPUSOnline course ID
     * @return the parsed student list response
     */
    public CampusOnlineStudentListResponse fetchStudents(String courseId) {
        String path = "/cdm/course/students/xml?courseID=" + courseId;
        String xml = fetchWithTokenFallback(path);
        return parseXml(xml, CampusOnlineStudentListResponse.class);
    }

    /**
     * Fetches metadata for a single CAMPUSOnline course.
     *
     * @param courseId the CAMPUSOnline course ID
     * @return the parsed course metadata response
     */
    public CampusOnlineCourseMetadataResponse fetchCourseMetadata(String courseId) {
        String path = "/cdm/course/xml?courseID=" + courseId;
        String xml = fetchWithTokenFallback(path);
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
        String path = "/xcal/organization/courses/xml?orgUnitID=" + orgUnitId + "&from=" + from + "&until=" + until;
        String xml = fetchWithTokenFallback(path);
        return parseXml(xml, CampusOnlineOrgCoursesResponse.class);
    }

    private String fetchWithTokenFallback(String path) {
        if (tokens == null || tokens.isEmpty()) {
            throw new CampusOnlineApiException("No CAMPUSOnline API tokens configured");
        }

        RestClientException lastException = null;
        for (String token : tokens) {
            try {
                String url = baseUrl + path + (path.contains("?") ? "&" : "?") + "token=" + token;
                String response = restTemplate.getForObject(url, String.class);
                if (response != null) {
                    return response;
                }
            }
            catch (RestClientException e) {
                log.warn("CAMPUSOnline API call failed with token, trying next: {}", e.getMessage());
                lastException = e;
            }
        }
        throw new CampusOnlineApiException("All CAMPUSOnline API tokens failed for path: " + path, lastException);
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
