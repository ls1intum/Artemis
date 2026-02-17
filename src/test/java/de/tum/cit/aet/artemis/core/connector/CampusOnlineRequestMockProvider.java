package de.tum.cit.aet.artemis.core.connector;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCourse;

/**
 * Mock provider for CAMPUSOnline API requests used in integration tests.
 * Intercepts outgoing HTTP calls from {@link de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineClient}
 * and returns predefined XML responses.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy
public class CampusOnlineRequestMockProvider {

    private final RestTemplate restTemplate;

    @Value("${artemis.campus-online.base-url:}")
    private String baseUrl;

    @Value("${artemis.campus-online.tokens:}")
    private List<String> tokens;

    private MockRestServiceServer mockServer;

    public CampusOnlineRequestMockProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Enables mocking of REST requests to the CAMPUSOnline API.
     * Must be called before setting up mock expectations.
     */
    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    /**
     * Resets the mock server to allow new expectations.
     */
    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    /**
     * Verifies that all expected requests were made.
     */
    public void verify() {
        if (mockServer != null) {
            mockServer.verify();
        }
    }

    /**
     * Mocks the organizational courses endpoint to return the given courses as XML.
     *
     * @param orgUnitId the expected organizational unit ID
     * @param from      the expected start date
     * @param until     the expected end date
     * @param courses   the courses to return
     */
    public void mockFetchCoursesForOrg(String orgUnitId, String from, String until, List<CampusOnlineOrgCourse> courses) {
        String token = tokens.isEmpty() ? "" : tokens.getFirst();
        String url = baseUrl + "/xcal/organization/courses/xml?orgUnitID=" + orgUnitId + "&from=" + from + "&until=" + until + "&token=" + token;
        String xml = buildOrgCoursesXml(courses);

        mockServer.expect(ExpectedCount.once(), requestTo(url)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(xml, MediaType.APPLICATION_XML));
    }

    /**
     * Mocks the student list endpoint to return students as XML.
     *
     * @param courseId the CAMPUSOnline course ID
     * @param xml      the raw XML response to return
     */
    public void mockFetchStudents(String courseId, String xml) {
        String token = tokens.isEmpty() ? "" : tokens.getFirst();
        String url = baseUrl + "/cdm/course/students/xml?courseID=" + courseId + "&token=" + token;

        mockServer.expect(ExpectedCount.once(), requestTo(url)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(xml, MediaType.APPLICATION_XML));
    }

    /**
     * Mocks the course metadata endpoint to return metadata as XML.
     *
     * @param courseId   the CAMPUSOnline course ID
     * @param courseName the course name to return
     * @param term       the teaching term to return
     */
    public void mockFetchCourseMetadata(String courseId, String courseName, String term) {
        String token = tokens.isEmpty() ? "" : tokens.getFirst();
        String url = baseUrl + "/cdm/course/xml?courseID=" + courseId + "&token=" + token;
        String xml = """
                <cdm>
                    <courseName>%s</courseName>
                    <teachingTerm>%s</teachingTerm>
                    <courseLanguage>EN</courseLanguage>
                    <courseID>%s</courseID>
                </cdm>
                """.formatted(courseName, term, courseId);

        mockServer.expect(ExpectedCount.once(), requestTo(url)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(xml, MediaType.APPLICATION_XML));
    }

    /**
     * Mocks the organizational courses endpoint to return a server error.
     *
     * @param orgUnitId the expected organizational unit ID
     * @param from      the expected start date
     * @param until     the expected end date
     */
    public void mockFetchCoursesForOrgServerError(String orgUnitId, String from, String until) {
        String token = tokens.isEmpty() ? "" : tokens.getFirst();
        String url = baseUrl + "/xcal/organization/courses/xml?orgUnitID=" + orgUnitId + "&from=" + from + "&until=" + until + "&token=" + token;

        mockServer.expect(ExpectedCount.once(), requestTo(url)).andExpect(method(HttpMethod.GET)).andRespond(withServerError());
    }

    /**
     * Mocks the student list endpoint to return a server error.
     *
     * @param courseId the CAMPUSOnline course ID
     */
    public void mockFetchStudentsServerError(String courseId) {
        String token = tokens.isEmpty() ? "" : tokens.getFirst();
        String url = baseUrl + "/cdm/course/students/xml?courseID=" + courseId + "&token=" + token;

        mockServer.expect(ExpectedCount.once(), requestTo(url)).andExpect(method(HttpMethod.GET)).andRespond(withServerError());
    }

    private String buildOrgCoursesXml(List<CampusOnlineOrgCourse> courses) {
        StringBuilder sb = new StringBuilder("<courses>");
        for (CampusOnlineOrgCourse course : courses) {
            sb.append("<course>");
            sb.append("<courseID>").append(course.courseId()).append("</courseID>");
            sb.append("<courseName>").append(course.courseName()).append("</courseName>");
            sb.append("<teachingTerm>").append(course.teachingTerm()).append("</teachingTerm>");
            sb.append("</course>");
        }
        sb.append("</courses>");
        return sb.toString();
    }
}
